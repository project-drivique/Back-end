# Paso 1: compilar el proyecto con Maven (la compilación ocurre dentro de la imagen, no hace falta subir /target)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Paso 2: imagen ligera para ejecutar la aplicación
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/drivique-api-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
