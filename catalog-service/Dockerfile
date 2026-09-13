FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY catalog-service/pom.xml catalog-service/pom.xml
WORKDIR /build/catalog-service
RUN ../mvnw -B dependency:go-offline
COPY catalog-service/src ./src
RUN ../mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/catalog-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
