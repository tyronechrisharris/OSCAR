FROM caddy:2-alpine

# Install fonts as required by AI_CONTRIBUTING_RULES.md
# Swap https for http in the package repositories, then install the font
RUN sed -i 's/https/http/g' /etc/apk/repositories && \
    apk add --no-cache font-freefont

# Create log directory
RUN mkdir -p /var/log/caddy

# Copy Caddyfile
COPY Caddyfile /etc/caddy/Caddyfile
