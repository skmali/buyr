# Buyr

A microservices-based e-commerce demo built with Spring Boot and Spring Cloud, showcasing service discovery, inter-service communication, caching, and a barcode-scanning product catalog UI.

## Architecture

Maven multi-module monorepo with five services, all registering with and discovering each other through Eureka:

| Service | Port | Responsibility | Storage |
|---|---|---|---|
| service-registry | 8761 | Eureka service discovery | — |
| catalog-service | 8080 | Product catalog, stock management, UPC lookup | PostgreSQL + Redis |
| cart-service | 8082 | Shopping cart | Redis |
| order-service | 8083 | Order placement and history | H2 (in-memory) |
| catalog-ui | 8081 | Thymeleaf/HTMX storefront UI | — |

All inter-service calls go through Eureka via a `@LoadBalanced RestTemplate` and logical service names (e.g. `http://CatalogService/...`) — no hardcoded hosts.

## Features

- Product catalog with add/list/get/reduce-stock endpoints
- Cart that enriches items with live product name/price from catalog-service
- Order placement that decrements catalog stock
- In-browser barcode/QR scanning (Quagga2 + html5-qrcode) plus USB HID barcode scanner support
- "Autofill from Web": scan a UPC and pre-fill the Add Product form via an external UPC lookup (UPCitemdb)

## Running locally

Requires **JDK 17** (Lombok in this Spring Boot 3.3.4 setup does not support newer JDKs like 26) and Docker for Postgres/Redis:

docker run -d --name buyr-postgres -e POSTGRES_DB=catalogdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine
docker run -d --name buyr-redis -p 6379:6379 redis:7-alpine

./mvnw clean install
# then run each module's jar, starting with service-registry


