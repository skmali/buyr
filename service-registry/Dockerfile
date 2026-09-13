FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY service-registry/pom.xml service-registry/pom.xml
WORKDIR /build/service-registry
RUN ../mvnw -B dependency:go-offline
COPY service-registry/src ./src
RUN ../mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/service-registry/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
