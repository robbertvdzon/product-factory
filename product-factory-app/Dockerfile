FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /source
COPY pom.xml ./
COPY product-factory-api/pom.xml product-factory-api/pom.xml
COPY product-impl/pom.xml product-impl/pom.xml
COPY decisions-impl/pom.xml decisions-impl/pom.xml
COPY agent-memory-impl/pom.xml agent-memory-impl/pom.xml
COPY ai-execution-impl/pom.xml ai-execution-impl/pom.xml
COPY product-factory-app/pom.xml product-factory-app/pom.xml
COPY product-factory-api/src product-factory-api/src
COPY product-impl/src product-impl/src
COPY decisions-impl/src decisions-impl/src
COPY agent-memory-impl/src agent-memory-impl/src
COPY ai-execution-impl/src ai-execution-impl/src
COPY product-factory-app/src product-factory-app/src
RUN mvn -B --no-transfer-progress -pl product-factory-app -am package -DskipTests

FROM eclipse-temurin:21.0.8_9-jre-alpine@sha256:990397e0495ac088ab6ee3d949a2e97b715a134d8b96c561c5d130b3786a489d
ARG APPLICATION_VERSION=0.1.0
ARG SOURCE_REVISION
ARG BUILD_TIME
ENV PF_APPLICATION_VERSION=${APPLICATION_VERSION} \
    PF_GIT_REVISION=${SOURCE_REVISION} \
    PF_BUILD_TIME=${BUILD_TIME} \
    HOME=/tmp
WORKDIR /app
COPY --from=build --chown=10001:0 /source/product-factory-app/target/product-factory-app-*.jar /app/product-factory.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-Djava.io.tmpdir=/tmp","-jar","/app/product-factory.jar"]
