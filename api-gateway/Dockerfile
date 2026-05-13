FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
