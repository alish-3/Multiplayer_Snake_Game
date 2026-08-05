# Multi-stage Docker build for the Multiplayer Snake Game.
# Stage 1 builds the WAR with Maven; Stage 2 runs it on Tomcat.
# Tomcat 11 implements Jakarta EE 10 (Servlet 6.0 / WebSocket 2.1) — required by this app.
# Env vars (DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET) are injected at runtime via docker-compose.

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom first for better layer caching of the dependency cache below.
COPY pom.xml .
# Best-effort dependency cache; go-offline can miss plugin deps, the next step fetches what's missing.
RUN mvn -B dependency:go-offline || true

COPY src ./src

# Tests run in CI (see .github/workflows/ci.yml), not in the image build.
RUN mvn -B clean package -DskipTests

# ---- Stage 2: run ----
FROM tomcat:11.0-jdk17-temurin

# ROOT.war = app served at "/" (frontend derives API/WebSocket URLs from window.location).
COPY --from=build /app/target/Multiplayer_Snake_Game.war $CATALINA_HOME/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
