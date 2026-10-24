# ---- Build stage: compile the app with Maven inside the container ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Tests run locally (the database test needs Docker), so the image build skips them.
RUN mvn -B -q -DskipTests package

# ---- Run stage: only a Java runtime and the built jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Keep memory within small free-tier instances (512 MB).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]