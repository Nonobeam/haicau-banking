FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY hcau-common/pom.xml hcau-common/pom.xml
COPY hcau-banking-common/pom.xml hcau-banking-common/pom.xml
COPY hcau-banking-reconcile-common/pom.xml hcau-banking-reconcile-common/pom.xml
COPY hcau-saga-orchestrator/pom.xml hcau-saga-orchestrator/pom.xml
COPY hcau-banking-reconcile/pom.xml hcau-banking-reconcile/pom.xml
COPY hcau-common/src hcau-common/src
COPY hcau-banking-common/src hcau-banking-common/src
COPY hcau-banking-reconcile-common/src hcau-banking-reconcile-common/src
COPY hcau-saga-orchestrator/src hcau-saga-orchestrator/src
COPY hcau-banking-reconcile/src hcau-banking-reconcile/src
RUN mvn -f pom.xml -DskipTests install -N && \
    mvn -f hcau-common/pom.xml -DskipTests install && \
    mvn -f hcau-banking-common/pom.xml -DskipTests install && \
    mvn -f hcau-banking-reconcile-common/pom.xml -DskipTests install && \
    mvn -f hcau-saga-orchestrator/pom.xml -DskipTests install && \
    mvn -f hcau-banking-reconcile/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/hcau-banking-reconcile/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
