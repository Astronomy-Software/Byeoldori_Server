#!/usr/bin/env python3
"""monitoring/cf-worker-exporter.py 누적 테스트. Cloudflare 호출(_post_graphql)을 가짜로 바꿔 네트워크 없이 돈다.
    python3 scripts/tests/cf_exporter_test.py
"""
import importlib.util
import os
import sys
from datetime import datetime, timedelta, timezone

sys.dont_write_bytecode = True  # monitoring/ 에 __pycache__ 를 남기지 않는다

os.environ.setdefault("CF_API_TOKEN", "test-token")
os.environ.setdefault("CF_ACCOUNT_ID", "test-account")
PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "monitoring", "cf-worker-exporter.py")
spec = importlib.util.spec_from_file_location("cf_exporter", PATH)
ex = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ex)

FAILS = 0


def check(name, cond):
    global FAILS
    if cond:
        print("  ok: %s" % name)
    else:
        print("  FAIL: %s" % name)
        FAILS += 1


WORKER = {"viewer": {"accounts": [{"workersInvocationsAdaptive": [
    {"dimensions": {"scriptName": "byeoldori-web", "status": "success"}, "sum": {"requests": 10, "errors": 0}},
    {"dimensions": {"scriptName": "byeoldori-web", "status": "scriptThrewException"}, "sum": {"requests": 2, "errors": 2}},
]}]}}


def zone_payload(count):
    return {"viewer": {"zones": [{"httpRequestsAdaptiveGroups": [
        {"count": count, "dimensions": {"edgeResponseStatus": 200}},
        {"count": 1, "dimensions": {"edgeResponseStatus": 502}},
    ]}]}}


def reset():
    ex.worker_requests.clear()
    ex.worker_errors.clear()
    ex.zone_status.clear()


def fake_graphql(fail_zone=None):
    def post(query, variables):
        if query is ex.WORKER_SUM_Q:
            return WORKER
        if variables.get("z") == fail_zone:
            raise RuntimeError("GraphQL rate limited")
        return zone_payload(5)
    return post


ZONES = {"z1": "byeoldori.com", "z2": "byeoltokki.com"}
END = datetime(2026, 10, 23, 0, 0, tzinfo=timezone.utc)
START = END - timedelta(minutes=5)

print("[1] 모든 조회 성공: Worker·존 counter 에 한 번 더한다")
reset()
ex._post_graphql = fake_graphql()
ex.accumulate_window(START, END, ZONES)
check("Worker 요청 수", ex.worker_requests[("byeoldori-web", "success")] == 10.0)
check("Worker 에러 수", ex.worker_errors[("byeoldori-web", "scriptThrewException")] == 2.0)
check("존별 상태코드", ex.zone_status[("byeoldori.com", "200")] == 5.0 and ex.zone_status[("byeoltokki.com", "502")] == 1.0)

print("[2] 두 번째 존 조회 실패: counter 는 그대로, 재조회 성공 시 한 번만 집계")
reset()
ex._post_graphql = fake_graphql(fail_zone="z2")
try:
    ex.accumulate_window(START, END, ZONES)
    raised = False
except RuntimeError:
    raised = True
check("예외로 끝나 호출자가 커서를 전진시키지 않는다", raised)
check("Worker counter 에 더하지 않음", not ex.worker_requests and not ex.worker_errors)
check("앞서 성공한 존도 더하지 않음", not ex.zone_status)
ex._post_graphql = fake_graphql()
ex.accumulate_window(START, END, ZONES)
check("같은 구간 재조회 후 Worker 요청 수가 이중 집계되지 않음", ex.worker_requests[("byeoldori-web", "success")] == 10.0)
check("같은 구간 재조회 후 존 counter 가 이중 집계되지 않음", ex.zone_status[("byeoldori.com", "200")] == 5.0)

print("[3] 수집이 오래 실패해 커서가 밀리면 상한(기본 60분)씩 잘라 조회한다")
check("짧은 구간은 그대로", ex.next_window_end(START, END) == END)
LONG_START = END - timedelta(days=2)
check("2일 밀린 구간은 60분만", ex.next_window_end(LONG_START, END) == LONG_START + timedelta(minutes=60))


def fake_graphql_all(query, variables):
    if query is ex.WORKER_SUM_Q:
        return WORKER
    if query is ex.WORKER_QUANTILE_Q:
        return {"viewer": {"accounts": [{"workersInvocationsAdaptive": []}]}}
    return zone_payload(5)


def fresh_state(zones=None):
    reset()
    ex.state.update({"up": 1, "scrape_errors": 0.0, "cursor": None, "zones": zones or {}})


def fake_rest(result=None, fail=False):
    def get(path):
        if fail:
            raise RuntimeError("HTTP 403")
        return {"result": result or []}
    return get


NOW = datetime(2026, 10, 23, 1, 0, 30, tzinfo=timezone.utc)

print("[4] 존 목록 조회가 실패하거나 비거나 감시 존이 없으면 그 주기는 실패(up=0)")
ex._post_graphql = fake_graphql_all
ex.WATCH_ZONES = ["byeoldori.com"]
for name, rest in (("조회 예외", fake_rest(fail=True)),
                   ("빈 목록(Zone Read 권한 없음)", fake_rest(result=[])),
                   ("감시 존 누락", fake_rest(result=[{"id": "z2", "name": "byeoltokki.com"}]))):
    fresh_state()
    ex._get_rest = rest
    ex.refresh_once(NOW)
    check("%s → up=0, 수집 오류 +1" % name, ex.state["up"] == 0 and ex.state["scrape_errors"] == 1.0)
    check("%s → 다음 주기에 다시 조회(존 캐시 안 함)" % name, ex.state["zones"] == {})
fresh_state()
ex._get_rest = fake_rest(result=[{"id": "z1", "name": "byeoldori.com"}, {"id": "z2", "name": "byeoltokki.com"}])
ex.refresh_once(NOW)
check("감시 존이 있으면 up=1", ex.state["up"] == 1 and ex.state["zones"] == ZONES)

print("[5] 기동(재시작) 첫 주기는 과거 구간을 다시 더하지 않는다(BACKFILL_MINUTES 기본 0)")
check("기본값 0", ex.BACKFILL_MINUTES == 0)
fresh_state(dict(ZONES))
ex.refresh_once(NOW)
SETTLED = datetime(2026, 10, 23, 0, 57, tzinfo=timezone.utc)  # 01:00 - 180초
check("커서는 현재 확정 시점에서 시작", ex.state["cursor"] == SETTLED)
check("첫 주기 counter 는 비어 있음", not ex.worker_requests and not ex.zone_status and ex.state["up"] == 1)
ex.refresh_once(NOW + timedelta(minutes=1))
check("다음 주기부터 새 1분 구간만 누적", ex.state["cursor"] == SETTLED + timedelta(minutes=1)
      and ex.worker_requests[("byeoldori-web", "success")] == 10.0)

if FAILS:
    print("cf_exporter_test: 실패 %d 건" % FAILS)
    sys.exit(1)
print("cf_exporter_test: 모두 통과")
