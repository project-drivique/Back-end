# Paso 1: compilar el proyecto con Maven (la compilación ocurre dentro de la imagen, no hace falta subir /target)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Paso 2: imagen ligera endurecida con usuario no-root y verificación de salud
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S drivique && adduser -S drivique -G drivique \
    && mkdir -p /app/uploads && chown -R drivique:drivique /app
COPY --from=build --chown=drivique:drivique /app/target/drivique-api-*.jar app.jar

USER drivique
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
