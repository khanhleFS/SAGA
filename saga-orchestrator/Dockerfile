FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY saga-orchestrator/target/saga-orchestrator-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

