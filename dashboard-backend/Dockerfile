FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY productfactory-contracts productfactory-contracts
COPY productfactory-common productfactory-common
COPY dashboard-backend dashboard-backend
COPY productfactory/pom.xml productfactory/pom.xml
COPY agentworker/pom.xml agentworker/pom.xml
RUN mvn -B --no-transfer-progress -pl dashboard-backend -am package -DskipTests
FROM eclipse-temurin:21-jre
RUN useradd --uid 10001 --create-home productfactory
USER 10001
COPY --from=build /src/dashboard-backend/target/productfactory-dashboard-backend-*.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app.jar"]
