# Estágio 1: build (Maven + JDK 21)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# Estágio 2: runtime (somente JRE 21, usuário sem privilégios)
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home geoprag
WORKDIR /app
# O nome app.jar vem de <finalName> no pom.xml.
COPY --from=build /workspace/target/app.jar app.jar
USER geoprag
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
