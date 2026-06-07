FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY hcau-common/pom.xml hcau-common/pom.xml
COPY hcau-platform-common/pom.xml hcau-platform-common/pom.xml
COPY hcau-saga-orchestrator/pom.xml hcau-saga-orchestrator/pom.xml
COPY hcau-platform-service/pom.xml hcau-platform-service/pom.xml
COPY hcau-common/src hcau-common/src
COPY hcau-platform-common/src hcau-platform-common/src
COPY hcau-saga-orchestrator/src hcau-saga-orchestrator/src
COPY hcau-platform-service/src hcau-platform-service/src
RUN mvn -f pom.xml -DskipTests install -N && \
    mvn -f hcau-common/pom.xml -DskipTests install && \
    mvn -f hcau-platform-common/pom.xml -DskipTests install && \
    mvn -f hcau-saga-orchestrator/pom.xml -DskipTests install && \
    mvn -f hcau-platform-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/hcau-platform-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
