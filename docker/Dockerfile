FROM python:3.12-slim-bookworm AS vosk-native
ARG VOSK_NATIVE_VERSION=0.3.45
RUN pip install --no-cache-dir --no-deps "vosk==${VOSK_NATIVE_VERSION}" \
    && mkdir -p /out \
    && cp "$(python -c 'import pathlib, vosk; print(pathlib.Path(vosk.__file__).parent / "libvosk.so")')" /out/libvosk.so

FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./
COPY assistant-common/pom.xml assistant-common/pom.xml
COPY assistant-server/pom.xml assistant-server/pom.xml
COPY assistant-client/pom.xml assistant-client/pom.xml
COPY assistant-common/src assistant-common/src
COPY assistant-server/src assistant-server/src
COPY assistant-client/src assistant-client/src
RUN mvn -B -pl assistant-server -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
ARG VOSK_MODEL_URL=https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
RUN apt-get update \
    && apt-get install -y --no-install-recommends espeak-ng libgomp1 curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/vosk-native /tmp/myyuyin-tts \
    && curl -fL --retry 3 "$VOSK_MODEL_URL" -o /tmp/vosk-model.zip \
    && unzip -q /tmp/vosk-model.zip -d /opt \
    && mv /opt/vosk-model-small-cn-0.22 /opt/vosk-model-small-cn \
    && rm /tmp/vosk-model.zip \
    && useradd --create-home --shell /usr/sbin/nologin assistant
COPY --from=vosk-native /out/libvosk.so /opt/vosk-native/libvosk.so
WORKDIR /app
COPY --from=builder /workspace/assistant-server/target/assistant-server-1.0.0.jar /app/app.jar
RUN chmod 0755 /opt/vosk-native/libvosk.so \
    && chown -R assistant:assistant /app /tmp/myyuyin-tts
USER assistant
ENV VOSK_MODEL_PATH=/opt/vosk-model-small-cn \
    TTS_OUTPUT_DIR=/tmp/myyuyin-tts \
    JAVA_TOOL_OPTIONS="-Djna.library.path=/opt/vosk-native -XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD test -f /opt/vosk-native/libvosk.so && curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
