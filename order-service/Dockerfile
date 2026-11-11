FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY order-service/target/order-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

