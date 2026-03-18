FROM caddy:2-alpine

# Install fonts and Tailscale (with HTTP bypass for corporate SSL inspection)
RUN sed -i 's/https/http/g' /etc/apk/repositories && \
    apk add --no-cache fonts-freefont-ttf tailscale

COPY Caddyfile /etc/caddy/Caddyfile
