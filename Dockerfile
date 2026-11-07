# Runtime image for the alerts service.
# The build stage compiles the executable Spring Boot jar; the runtime stage
# only carries the JRE and the jar so the published image stays small.

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -q package -Dmaven.test.skip=true

FROM eclipse-temurin:21.0.8_9-jre-noble
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

# Run as an unprivileged user. The numeric form avoids depending on
# useradd/groupadd being present in the base image.
USER 1000:1000

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
