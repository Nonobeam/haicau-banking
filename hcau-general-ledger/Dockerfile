FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY hcau-common/pom.xml hcau-common/pom.xml
COPY hcau-banking-common/pom.xml hcau-banking-common/pom.xml
COPY hcau-banking-module-common/pom.xml hcau-banking-module-common/pom.xml
COPY hcau-internal-client/pom.xml hcau-internal-client/pom.xml
COPY hcau-general-ledger/pom.xml hcau-general-ledger/pom.xml
COPY hcau-common/src hcau-common/src
COPY hcau-banking-common/src hcau-banking-common/src
COPY hcau-banking-module-common/src hcau-banking-module-common/src
COPY hcau-internal-client/src hcau-internal-client/src
COPY hcau-general-ledger/src hcau-general-ledger/src
RUN mvn -f pom.xml -DskipTests install -N && \
    mvn -f hcau-common/pom.xml -DskipTests install && \
    mvn -f hcau-banking-common/pom.xml -DskipTests install && \
    mvn -f hcau-banking-module-common/pom.xml -DskipTests install && \
    mvn -f hcau-internal-client/pom.xml -DskipTests install && \
    mvn -f hcau-general-ledger/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/hcau-general-ledger/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
