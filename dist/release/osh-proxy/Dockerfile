FROM caddy:2-alpine

# Install fonts as required by AI_CONTRIBUTING_RULES.md
RUN apk add --no-cache font-freefont

# Create log directory
RUN mkdir -p /var/log/caddy

# Copy Caddyfile
COPY Caddyfile /etc/caddy/Caddyfile

# The Caddy image already has /usr/bin/caddy as the entrypoint
