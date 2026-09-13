FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY cart-service/pom.xml cart-service/pom.xml
WORKDIR /build/cart-service
RUN ../mvnw -B dependency:go-offline
COPY cart-service/src ./src
RUN ../mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/cart-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
