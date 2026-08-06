FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY productfactory-contracts productfactory-contracts
COPY productfactory-common productfactory-common
COPY productfactory productfactory
COPY agentworker/pom.xml agentworker/pom.xml
COPY dashboard-backend/pom.xml dashboard-backend/pom.xml
RUN mvn -B --no-transfer-progress -pl productfactory -am package -DskipTests
FROM eclipse-temurin:21-jre
USER root
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*
RUN useradd --uid 10001 --create-home productfactory \
 && chgrp -R 0 /home/productfactory \
 && chmod -R g=u /home/productfactory
USER 10001
WORKDIR /home/productfactory
COPY --from=build /src/productfactory/target/productfactory-app-*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
