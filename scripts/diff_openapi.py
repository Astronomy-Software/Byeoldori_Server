import json
import sys

DOCS_BASE = "https://byeoldori-docs.pages.dev/docs/guide"

PATH_TO_DOCS = {
    "/weather":          f"{DOCS_BASE}/forecast",
    "/observationsites": f"{DOCS_BASE}/observation-sites",
    "/me/saved-sites":   f"{DOCS_BASE}/observation-sites",
    "/auth":             f"{DOCS_BASE}/authentication",
    "/users":            f"{DOCS_BASE}/authentication",
    "/calendar":         f"{DOCS_BASE}/calendar",
    "/community":        f"{DOCS_BASE}/community",
    "/posts":            f"{DOCS_BASE}/community",
    "/files":            f"{DOCS_BASE}/file-upload",
}

def load(path):
    try:
        return json.load(open(path))
    except Exception:
        return {'paths': {}}

def endpoints(spec):
    result = set()
    for path, methods in spec.get('paths', {}).items():
        for method in methods:
            if method in ('get', 'post', 'put', 'patch', 'delete'):
                result.add(f'{method.upper()} {path}')
    return result

def get_docs_links(eps):
    links = {}
    for ep in eps:
        path = ep.split(' ', 1)[1]
        for prefix, url in PATH_TO_DOCS.items():
            if path.startswith(prefix):
                # 경로 prefix의 마지막 부분을 페이지 이름으로 사용
                page = prefix.lstrip('/').split('/')[-1]
                links[page] = url
                break
    return links

old = load(sys.argv[1])
new = load(sys.argv[2])

added   = sorted(endpoints(new) - endpoints(old))
removed = sorted(endpoints(old) - endpoints(new))

lines = []
if added:
    lines.append(f'🟢 추가된 엔드포인트 ({len(added)})')
    lines += [f'  {e}' for e in added[:5]]
if removed:
    lines.append(f'🔴 삭제된 엔드포인트 ({len(removed)})')
    lines += [f'  {e}' for e in removed[:5]]
if not lines:
    lines.append('변경된 API 없음 (내부 로직/스키마만 업데이트)')

docs_links = get_docs_links(added + removed)

print(json.dumps({
    "summary": '\n'.join(lines),
    "links": docs_links,  # { "페이지명": "URL", ... }
    "has_changes": bool(added or removed)
}))
