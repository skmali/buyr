FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY mvnw .
COPY .mvn .mvn
COPY catalog-ui/pom.xml catalog-ui/pom.xml
WORKDIR /build/catalog-ui
RUN ../mvnw -B dependency:go-offline
COPY catalog-ui/src ./src
RUN ../mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/catalog-ui/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
