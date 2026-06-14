# Stage 1: build frontend
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: build backend (with frontend static files baked in)
FROM eclipse-temurin:17-jdk-alpine AS backend
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Stage 3: runtime
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app/backend

COPY --from=backend /build/backend/target/kagura-*.jar app.jar

ARG DICTS_URL=https://github.com/khmori/kagura/releases/download/v0.1.0-data/dicts.tar.gz
ARG GITHUB_TOKEN=""
RUN if [ -n "$GITHUB_TOKEN" ]; then \
      curl -L -H "Authorization: token $GITHUB_TOKEN" -o /tmp/dicts.tar.gz "$DICTS_URL"; \
    else \
      curl -L -o /tmp/dicts.tar.gz "$DICTS_URL"; \
    fi && \
    tar xzf /tmp/dicts.tar.gz -C /app/ && \
    rm /tmp/dicts.tar.gz

EXPOSE 8080
CMD java ${JAVA_OPTS:-} -jar app.jar
