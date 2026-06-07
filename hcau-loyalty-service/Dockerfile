FROM eclipse-temurin:21-jre
WORKDIR /app
COPY hcau-loyalty-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
