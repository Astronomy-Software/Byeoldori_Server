FROM ubuntu:22.04
LABEL maintainer="Astronomy-Software"

ENV DEBIAN_FRONTEND=noninteractive

# Stellarium + 가상 X 디스플레이 + Mesa 소프트웨어 렌더링
RUN apt-get update && apt-get install -y \
    stellarium \
    xvfb \
    libgles2-mesa \
    mesa-utils \
    curl \
    && rm -rf /var/lib/apt/lists/*

# skybox 출력 디렉토리
RUN mkdir -p /skybox/live /root/.stellarium

# Stellarium 설정 (RemoteControl 활성화)
COPY config.ini /root/.stellarium/config.ini

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# RemoteControl 포트
EXPOSE 8090

# 스카이박스 볼륨 (nginx와 공유)
VOLUME ["/skybox"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -sf http://localhost:8090/api/main/status || exit 1

CMD ["/entrypoint.sh"]
