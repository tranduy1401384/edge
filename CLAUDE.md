# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`edge` is the API gateway of the SupperApp lab (siblings: `../accounts`, `../profile`; each is an independent Maven project with no shared parent pom). It is Spring Boot 4.1.2 / Java 21 with Spring Cloud 2025.1.4, using **Spring Cloud Gateway Server WebMVC** (servlet stack, `spring-cloud-starter-gateway-server-webmvc`), not the reactive WebFlux gateway. Routes, filters and config therefore use the MVC flavour (`spring.cloud.gateway.server.webmvc.*` properties, `RouterFunction`/`HandlerFunctions` beans), and WebFlux-based gateway examples will not apply.

Currently a skeleton: `EdgeApplication`, an `application.yaml` with only `spring.application.name`, and a context-load test. No port, routes or security are configured yet.

Intended design (from `../../setup/note.md`): Postman → edge (port 9000) → Java service (8081), with JWTs issued by Keycloak (`../../setup/docker-compose.yml`, Keycloak on 8080) and verified via its JWKS endpoint.

## Commands

```
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=EdgeApplicationTests#contextLoads   # single test
./mvnw package
```

Lombok is an optional dependency; the pom configures it as an annotation processor on `maven-compiler-plugin`.
