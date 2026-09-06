# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:26-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -DskipTests package

FROM eclipse-temurin:26-jre-jammy

RUN groupadd --system fhemni && useradd --system --gid fhemni --home-dir /app fhemni
WORKDIR /app
COPY --from=build --chown=fhemni:fhemni /workspace/target/fhemni-*.jar /app/fhemni.jar

USER fhemni
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/fhemni.jar"]
