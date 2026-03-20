# Dockerfile for OpenSensorHub (OSH) Backend
FROM openjdk:11-jre-slim

# Install fonts as required by AI_CONTRIBUTING_RULES.md
RUN apt-get update && apt-get install -y fonts-freefont-ttf && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# The build artifacts should be copied into the image.
# We assume the build is done and artifacts are in their expected locations relative to the root.

COPY ./dist/scripts/standard/ /app/scripts/
COPY ./dist/config/standard/config.json /app/config.json
COPY ./dist/config/standard/logback.xml /app/logback.xml
COPY ./third-party-drivers/ /app/lib/

# We'll need the runtime classpath jars as well.
# For a real Docker build, we'd ideally have a task that collects all jars into a single folder.
# Given the constraints, I will assume the `lib` folder will be populated by the user or a build script.
# In a local test, the user would run a command to gather these.

# Expose the internal OSH port
EXPOSE 8282

# The entrypoint should reflect the java command in launch.sh
ENTRYPOINT ["java", "-Xms6g", "-Xmx6g", "-Xss256k", "-XX:ReservedCodeCacheSize=512m", "-XX:+UseG1GC", "-XX:+HeapDumpOnOutOfMemoryError", "-Dlogback.configurationFile=/app/logback.xml", "-cp", "/app/lib/*", "-Djava.system.class.loader=org.sensorhub.utils.NativeClassLoader", "com.botts.impl.security.SensorHubWrapper", "/app/config.json", "/app/db"]
