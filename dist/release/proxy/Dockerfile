FROM caddy:2-alpine

# Install fonts as required by AI_CONTRIBUTING_RULES.md
RUN apk add --no-cache fonts-freefont-ttf

# Tailscale is needed for federated mode TLS
RUN apk add --no-cache tailscale

COPY Caddyfile /etc/caddy/Caddyfile
