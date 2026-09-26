# ─── Stage 1: Build Application JAR with Maven ────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && mvn clean package -DskipTests \
    && rm -rf /var/lib/apt/lists/*

# ─── Stage 2: Runtime Environment for Hugging Face Spaces ────────────────────
FROM eclipse-temurin:21-jre-jammy

# Install Tesseract OCR, English data, PostgreSQL server, and utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    postgresql \
    postgresql-contrib \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Ensure PostgreSQL binaries are available in PATH across versions
ENV PATH="/usr/lib/postgresql/16/bin:/usr/lib/postgresql/15/bin:/usr/lib/postgresql/14/bin:$PATH"

# Set up standard Hugging Face non-root user (UID 1000)
RUN useradd -m -u 1000 user
USER user
ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH
WORKDIR /home/user/app

# Copy application JAR and entrypoint script
COPY --chown=user:user --from=build /build/target/ocr-processor-*.jar app.jar
COPY --chown=user:user entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

# Hugging Face Spaces exposes port 7860
EXPOSE 7860
ENV PORT=7860 \
    SERVER_PORT=7860

ENTRYPOINT ["./entrypoint.sh"]
