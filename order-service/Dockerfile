FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY order-service/pom.xml order-service/pom.xml
WORKDIR /build/order-service
RUN ../mvnw -B dependency:go-offline
COPY order-service/src ./src
RUN ../mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/order-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
