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

if FAILS:
    print("cf_exporter_test: 실패 %d 건" % FAILS)
    sys.exit(1)
print("cf_exporter_test: 모두 통과")
