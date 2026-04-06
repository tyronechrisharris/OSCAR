FROM postgis/postgis:16-3.4

# Install fonts as required by AI_CONTRIBUTING_RULES.md
RUN apt-get update && apt-get install -y fonts-freefont-ttf && rm -rf /var/lib/apt/lists/*

COPY init-extensions.sql /docker-entrypoint-initdb.d/init-extensions.sql

ENV POSTGRES_INITDB_ARGS="--auth-local=trust --auth-host=scram-sha-256"
