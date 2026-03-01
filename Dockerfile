FROM eclipse-temurin:21-jre-jammy

# Install explicitly requested font package and other dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    fonts-freefont-ttf \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN groupadd -r oscar && useradd -r -g oscar oscar

# Set working directory
WORKDIR /app

# Copy application files (assuming a built distribution is available at this path context)
# COPY build/distributions/osh-node-oscar-*/osh-node-oscar-*/ /app/
# Ensure appropriate permissions
# RUN chown -R oscar:oscar /app

# Switch to non-root user
USER oscar

# Expose the API port
EXPOSE 8282

# Define entrypoint (adjust based on actual launch script location after copy)
# ENTRYPOINT ["./launch.sh"]
