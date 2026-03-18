FROM eclipse-temurin:21-jre-alpine

# Install fonts as required by AI_CONTRIBUTING_RULES.md (with HTTP bypass for corporate SSL inspection)
RUN sed -i 's/https/http/g' /etc/apk/repositories && \
    apk add --no-cache fonts-freefont-ttf bash openssl

WORKDIR /app

# The build artifacts will be copied here by docker-compose or a build script
# We expect the 'lib', 'config.json', 'logback.xml', etc. to be in the current directory during build
COPY . .

# Ensure scripts are executable
RUN chmod +x *.sh || true

EXPOSE 8282

# Start the OSH Backend using the same logic as launch.sh but adapted for container
ENTRYPOINT ["/bin/bash", "-c", " \
    ./load_trusted_certs.sh && \
    if [ ! -f \"osh-keystore.p12\" ]; then \
        echo \"Generating ephemeral certificates...\"; \
        java -cp \"lib/*\" com.botts.impl.security.EphemeralCAUtility; \
    fi && \
    if [ -f \".app_secrets\" ]; then \
        export KEYSTORE_PASSWORD=$(head -n 1 .app_secrets); \
    else \
        export KEYSTORE_PASSWORD=\"atakatak\"; \
    fi && \
    export KEYSTORE=\"./osh-keystore.p12\" && \
    export KEYSTORE_TYPE=PKCS12 && \
    export TRUSTSTORE=\"./truststore.jks\" && \
    export TRUSTSTORE_TYPE=JKS && \
    export TRUSTSTORE_PASSWORD=\"changeit\" && \
    java -Xms6g -Xmx6g -Xss256k -XX:ReservedCodeCacheSize=512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError \
        -Dlogback.configurationFile=./logback.xml \
        -cp \"lib/*\" \
        -Djava.system.class.loader=\"org.sensorhub.utils.NativeClassLoader\" \
        com.botts.impl.security.SensorHubWrapper ./config.json ./db \
"]
