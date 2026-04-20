FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/loan-service-ms-1.0.0.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
