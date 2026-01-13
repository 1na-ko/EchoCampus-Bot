FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY target/echocampus-bot-1.0.0.jar app.jar

RUN mkdir -p /app/uploads /app/logs && \
    chown -R spring:spring /app/uploads /app/logs

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
