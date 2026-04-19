import json
import sys

def load(path):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception:
        return {'paths': {}}

def endpoints(spec):
    result = set()
    for path, methods in spec.get('paths', {}).items():
        for method in methods:
            if method in ('get', 'post', 'put', 'patch', 'delete'):
                result.add(f'{method.upper()} {path}')
    return result

old = load(sys.argv[1])
new = load(sys.argv[2])

added   = sorted(endpoints(new) - endpoints(old))
removed = sorted(endpoints(old) - endpoints(new))

lines = []
if added:
    lines.append(f'🟢 추가된 엔드포인트 ({len(added)})')
    for e in added[:5]:
        lines.append(f'  {e}')
if removed:
    lines.append(f'🔴 삭제된 엔드포인트 ({len(removed)})')
    for e in removed[:5]:
        lines.append(f'  {e}')
if not lines:
    lines.append('변경된 API 없음 (내부 로직/스키마만 업데이트)')

print('\n'.join(lines))
