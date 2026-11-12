# Multi-stage build: React bundle -> Spring Boot static resources -> one runnable jar.
FROM node:22-alpine AS web
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npx vite build

FROM eclipse-temurin:21-jdk AS api
WORKDIR /app/backend
COPY backend/ ./
COPY --from=web /app/backend/src/main/resources/static ./src/main/resources/static
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /app/backend/target/itsm-1.0.0.jar app.jar
EXPOSE 8080
HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
