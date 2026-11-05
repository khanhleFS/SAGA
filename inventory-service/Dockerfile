FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY inventory-service/target/inventory-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

