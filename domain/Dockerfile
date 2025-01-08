# 1) Сборочный контейнер с Maven + JDK 17
FROM maven:3.9.3-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2) Финальный контейнер с JRE 17
FROM eclipse-temurin:17-jdk
WORKDIR /app
# Копируем JAR с зависимостями (maven-assembly-plugin создаст его)
COPY --from=builder /app/target/domain-1.0.0-jar-with-dependencies.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]