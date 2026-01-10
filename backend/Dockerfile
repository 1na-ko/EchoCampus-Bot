FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY target/echocampus-bot-1.0.0.jar app.jar

RUN addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app/uploads /app/logs && \
    chown -R spring:spring /app/uploads /app/logs

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
