FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

RUN apt-get update && apt-get install -y maven

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/proxyserver-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9090

ENV TUNNEL_PORT=9090

ENTRYPOINT ["java", "-jar", "app.jar"]
