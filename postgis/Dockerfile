FROM kartoza/postgis:16-3.4
ENV POSTGRES_INITDB_ARGS="--auth-host=scram-sha-256 --auth-local=scram-sha-256"

COPY entrypoint-quiet.sh /usr/local/bin/entrypoint-quiet.sh
RUN chmod +x /usr/local/bin/entrypoint-quiet.sh

ENTRYPOINT ["entrypoint-quiet.sh"]
CMD ["postgres"]
