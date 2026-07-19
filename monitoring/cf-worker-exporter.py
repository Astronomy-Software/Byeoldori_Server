#!/usr/bin/env python3
"""
경량 Cloudflare Worker/Zone 메트릭 exporter (의존성 0, stdlib만 사용).

lablabs/cloudflare-exporter 0.2.3은 worker 메트릭을 항상 0으로 내보낸다.
원인: 해당 exporter는 `now-scrape_delay` 기준 '고정 1분 창'만 GraphQL로 조회하고
Counter에 Add 한다. 무료플랜 저트래픽 worker는 임의의 1분 창이 거의 항상 비어
있어(실측: 1분창 0건, 1시간창 67건) 시계열 자체가 생성되지 않는다. 창 폭 60초는
소스에 하드코딩돼 env로 넓힐 수 없다 → 구조적으로 부적합.

이 exporter는 '연속(비겹침) 창'을 누적한다: 매 refresh마다 [cursor, now-settle]
구간을 조회해 counter에 더하고 cursor를 전진시킨다. 저트래픽이라도 모든 트래픽이
정확히 한 번씩 집계돼 단조증가 counter가 되고 Grafana rate()가 정상 동작한다.

노출 메트릭(lablabs 이름과 호환 → frontend-cf 대시보드 수정 불필요):
  cloudflare_worker_requests_count{script_name,status}   counter (누적)
  cloudflare_worker_errors_count{script_name,status}     counter (누적)
  cloudflare_worker_cpu_time{script_name,status,quantile} gauge  (최근 1h 분위수, µs)
  cloudflare_zone_requests_status{zone,status}           counter (누적, edgeResponseStatus)
  cloudflare_worker_exporter_up                          gauge  (마지막 수집 성공=1)
  cloudflare_worker_exporter_scrape_errors_total         counter
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

GRAPHQL = "https://api.cloudflare.com/client/v4/graphql"
REST = "https://api.cloudflare.com/client/v4"

TOKEN = os.environ.get("CF_API_TOKEN", "").strip()
ACCOUNT_ID = (os.environ.get("CF_ACCOUNT_ID") or os.environ.get("CF_ACCOUNTS", "").split(",")[0]).strip()
PORT = int(os.environ.get("PORT", "8080"))
REFRESH_SECONDS = int(os.environ.get("REFRESH_SECONDS", "60"))
# 애널리틱스 수집 지연 보정: 이 초 이전 데이터만 '확정'으로 간주해 누적한다.
SETTLE_SECONDS = int(os.environ.get("SETTLE_SECONDS", "180"))
# 기동 시 과거 N분을 1회 백필해 대시보드/검증에 즉시 데이터가 뜨게 한다.
BACKFILL_MINUTES = int(os.environ.get("BACKFILL_MINUTES", "60"))
HTTP_TIMEOUT = int(os.environ.get("HTTP_TIMEOUT", "20"))

if not TOKEN or not ACCOUNT_ID:
    sys.stderr.write("FATAL: CF_API_TOKEN / CF_ACCOUNT_ID(또는 CF_ACCOUNTS) 필요\n")
    sys.exit(1)

_lock = threading.Lock()
# counter 저장소: {(labels_tuple): value}
worker_requests = {}   # (script_name, status) -> float
worker_errors = {}     # (script_name, status) -> float
zone_status = {}       # (zone_name, status) -> float
# gauge 저장소
worker_cpu = {}        # (script_name, status, quantile) -> float
state = {"up": 0, "scrape_errors": 0.0, "cursor": None, "zones": {}}


def _minute_floor(dt):
    return dt.replace(second=0, microsecond=0)


def _iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:00Z")


def _post_graphql(query, variables):
    body = json.dumps({"query": query, "variables": variables}).encode()
    req = urllib.request.Request(
        GRAPHQL, data=body,
        headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as r:
        payload = json.load(r)
    if payload.get("errors"):
        raise RuntimeError("GraphQL errors: %s" % payload["errors"])
    return payload["data"]


def _get_rest(path):
    req = urllib.request.Request(REST + path, headers={"Authorization": "Bearer " + TOKEN})
    with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as r:
        return json.load(r)


def discover_zones():
    """account의 zone 목록 {zone_id: zone_name} 반환."""
    try:
        data = _get_rest("/zones?account.id=%s&per_page=50" % ACCOUNT_ID)
        return {z["id"]: z["name"] for z in data.get("result", [])}
    except Exception as e:  # noqa: BLE001
        sys.stderr.write("zone discovery 실패: %s\n" % e)
        return {}


WORKER_SUM_Q = """
query ($a: String!, $min: Time!, $max: Time!) {
  viewer { accounts(filter: {accountTag: $a}) {
    workersInvocationsAdaptive(limit: 1000, filter: {datetime_geq: $min, datetime_lt: $max}) {
      dimensions { scriptName status }
      sum { requests errors }
    }
  } }
}"""

WORKER_QUANTILE_Q = """
query ($a: String!, $min: Time!, $max: Time!) {
  viewer { accounts(filter: {accountTag: $a}) {
    workersInvocationsAdaptive(limit: 1000, filter: {datetime_geq: $min, datetime_lt: $max}) {
      dimensions { scriptName status }
      quantiles { cpuTimeP50 cpuTimeP75 cpuTimeP99 cpuTimeP999 }
    }
  } }
}"""

ZONE_STATUS_Q = """
query ($z: String!, $min: Time!, $max: Time!) {
  viewer { zones(filter: {zoneTag: $z}) {
    httpRequestsAdaptiveGroups(limit: 1000, filter: {datetime_geq: $min, datetime_lt: $max}) {
      count dimensions { edgeResponseStatus }
    }
  } }
}"""


def accumulate_worker(min_dt, max_dt):
    data = _post_graphql(WORKER_SUM_Q, {"a": ACCOUNT_ID, "min": _iso(min_dt), "max": _iso(max_dt)})
    accts = data["viewer"]["accounts"]
    rows = accts[0]["workersInvocationsAdaptive"] if accts else []
    with _lock:
        for row in rows:
            script = row["dimensions"]["scriptName"] or "unknown"
            status = row["dimensions"]["status"] or "unknown"
            reqs = float(row["sum"]["requests"])
            errs = float(row["sum"]["errors"])
            worker_requests[(script, status)] = worker_requests.get((script, status), 0.0) + reqs
            worker_errors[(script, status)] = worker_errors.get((script, status), 0.0) + errs


def accumulate_zones(min_dt, max_dt, zones):
    for zid, zname in zones.items():
        data = _post_graphql(ZONE_STATUS_Q, {"z": zid, "min": _iso(min_dt), "max": _iso(max_dt)})
        znodes = data["viewer"]["zones"]
        groups = znodes[0]["httpRequestsAdaptiveGroups"] if znodes else []
        with _lock:
            for g in groups:
                status = str(g["dimensions"]["edgeResponseStatus"])
                zone_status[(zname, status)] = zone_status.get((zname, status), 0.0) + float(g["count"])


def refresh_quantiles(max_dt):
    """최근 1시간 분위수를 gauge로 Set."""
    min_dt = max_dt - timedelta(hours=1)
    data = _post_graphql(WORKER_QUANTILE_Q, {"a": ACCOUNT_ID, "min": _iso(min_dt), "max": _iso(max_dt)})
    accts = data["viewer"]["accounts"]
    rows = accts[0]["workersInvocationsAdaptive"] if accts else []
    fresh = {}
    for row in rows:
        script = row["dimensions"]["scriptName"] or "unknown"
        status = row["dimensions"]["status"] or "unknown"
        q = row["quantiles"]
        for name, key in (("P50", "cpuTimeP50"), ("P75", "cpuTimeP75"),
                          ("P99", "cpuTimeP99"), ("P999", "cpuTimeP999")):
            if q.get(key) is not None:
                fresh[(script, status, name)] = float(q[key])
    with _lock:
        worker_cpu.clear()
        worker_cpu.update(fresh)


def refresh_loop():
    while True:
        try:
            if not state["zones"]:
                state["zones"] = discover_zones()
            now = datetime.now(timezone.utc)
            end = _minute_floor(now) - timedelta(seconds=SETTLE_SECONDS)
            end = _minute_floor(end)
            if state["cursor"] is None:
                state["cursor"] = end - timedelta(minutes=BACKFILL_MINUTES)
            if end > state["cursor"]:
                accumulate_worker(state["cursor"], end)
                accumulate_zones(state["cursor"], end, state["zones"])
                state["cursor"] = end
            refresh_quantiles(end)
            state["up"] = 1
        except Exception as e:  # noqa: BLE001
            state["up"] = 0
            state["scrape_errors"] += 1
            sys.stderr.write("[%s] refresh 오류: %s\n" % (datetime.now(timezone.utc).isoformat(), e))
        time.sleep(REFRESH_SECONDS)


def _esc(v):
    return str(v).replace("\\", "\\\\").replace('"', '\\"')


def render_metrics():
    lines = []
    with _lock:
        lines.append("# HELP cloudflare_worker_requests_count Worker 요청 수(누적)")
        lines.append("# TYPE cloudflare_worker_requests_count counter")
        for (script, status), v in sorted(worker_requests.items()):
            lines.append('cloudflare_worker_requests_count{script_name="%s",status="%s"} %g'
                         % (_esc(script), _esc(status), v))

        lines.append("# HELP cloudflare_worker_errors_count Worker 에러 수(누적)")
        lines.append("# TYPE cloudflare_worker_errors_count counter")
        for (script, status), v in sorted(worker_errors.items()):
            lines.append('cloudflare_worker_errors_count{script_name="%s",status="%s"} %g'
                         % (_esc(script), _esc(status), v))

        lines.append("# HELP cloudflare_worker_cpu_time Worker CPU time 분위수(µs, 최근 1h)")
        lines.append("# TYPE cloudflare_worker_cpu_time gauge")
        for (script, status, q), v in sorted(worker_cpu.items()):
            lines.append('cloudflare_worker_cpu_time{script_name="%s",status="%s",quantile="%s"} %g'
                         % (_esc(script), _esc(status), _esc(q), v))

        lines.append("# HELP cloudflare_zone_requests_status Zone edge 응답 상태코드별 요청 수(누적)")
        lines.append("# TYPE cloudflare_zone_requests_status counter")
        for (zone, status), v in sorted(zone_status.items()):
            lines.append('cloudflare_zone_requests_status{zone="%s",status="%s"} %g'
                         % (_esc(zone), _esc(status), v))

        lines.append("# HELP cloudflare_worker_exporter_up 마지막 수집 성공 여부")
        lines.append("# TYPE cloudflare_worker_exporter_up gauge")
        lines.append("cloudflare_worker_exporter_up %d" % state["up"])

        lines.append("# HELP cloudflare_worker_exporter_scrape_errors_total 수집 실패 누적")
        lines.append("# TYPE cloudflare_worker_exporter_scrape_errors_total counter")
        lines.append("cloudflare_worker_exporter_scrape_errors_total %g" % state["scrape_errors"])
    return "\n".join(lines) + "\n"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802
        if self.path.rstrip("/") in ("/metrics", ""):
            body = render_metrics().encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, *args):  # 접근 로그 억제
        pass


def main():
    t = threading.Thread(target=refresh_loop, daemon=True)
    t.start()
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    sys.stderr.write("cf-worker-exporter listening on :%d/metrics (refresh=%ds, settle=%ds)\n"
                     % (PORT, REFRESH_SECONDS, SETTLE_SECONDS))
    srv.serve_forever()


if __name__ == "__main__":
    main()
