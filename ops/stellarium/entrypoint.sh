#!/bin/bash
set -e

echo "[Stellarium] 가상 X 디스플레이(Xvfb) 시작..."
Xvfb :99 -screen 0 1280x720x24 -nolisten tcp &
XVFB_PID=$!
sleep 3

echo "[Stellarium] Xvfb started (PID: $XVFB_PID)"

# Mesa 소프트웨어 렌더링 (GPU 없는 GCP VM 대응)
export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLSL_VERSION_OVERRIDE=450

echo "[Stellarium] Stellarium 시작 (소프트웨어 렌더링)..."
stellarium \
    --screenshot-dir /skybox/live \
    &

STEL_PID=$!
echo "[Stellarium] Stellarium started (PID: $STEL_PID)"

# RemoteControl 플러그인 활성화 대기
echo "[Stellarium] RemoteControl 활성화 대기 중..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8090/api/main/status > /dev/null 2>&1; then
        echo "[Stellarium] RemoteControl 활성화 완료 (${i}초)"
        break
    fi
    sleep 1
done

echo "[Stellarium] Ready. RemoteControl: http://localhost:8090"

# Stellarium 프로세스 감시 (종료 시 컨테이너도 종료)
wait $STEL_PID
EXIT_CODE=$?
echo "[Stellarium] Stellarium 종료 (exit: $EXIT_CODE)"
kill $XVFB_PID 2>/dev/null || true
exit $EXIT_CODE
