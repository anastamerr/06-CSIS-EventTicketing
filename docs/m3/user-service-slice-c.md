# User-Service Slice C

This slice is the user-service cross-cutting infrastructure track for Milestone 3.

## What this assignment is about

Milestone 3 turns the project into a real microservices platform:

- Each service owns its own PostgreSQL database.
- Services communicate through Feign and RabbitMQ instead of cross-service SQL joins.
- Spring Cloud Gateway becomes the public entry point.
- Kubernetes runs the platform locally on Minikube.
- Prometheus, Loki, and Grafana provide observability.

Your assigned `S1-C` slice focuses on the user-service pieces that plug into the shared infrastructure:

- the user-service gateway route
- the user-service Prometheus scrape target
- the user-service Grafana dashboard registration
- documentation of the user-service paths that the gateway owns
- a contract check for the shared user-service route and RabbitMQ labels

## User-service route ownership

The gateway block for this slice owns the following public path families:

- `/api/auth/**`
- `/api/users/**`

These patterns cover the current user-service controllers:

- authentication: register and login
- user CRUD and reporting endpoints
- booking summary and activity endpoints
- favorite venue endpoints under `/api/users/{userId}/venues/**`
- user health endpoint at `/api/users/health`

## Shared contract labels checked

From the M3 spec, the shared user-service infrastructure labels checked for this slice are:

- gateway route id: `user-service`
- gateway upstream URI label: `http://user-service:8080`
- user-service event exchange: `user.events`
- user-service saga consumer queue: `user.booking.saga-listener`
- user-service saga dead-letter queue: `user.booking.saga-listener.dlq`

## Files started for this slice

- `api-gateway/src/main/resources/application.yml`
- `k8s/configmaps/gateway-configmap.yaml`
- `k8s/monitoring/prometheus/prometheus-configmap.yaml`
- `k8s/monitoring/grafana/grafana-dashboards.yaml`
- `k8s/monitoring/grafana/dashboards/user-dashboard.json`

## Gateway config note

This gateway module uses the Spring Cloud Gateway 5 / Spring Boot 4 configuration path:

- `spring.cloud.gateway.server.webflux.routes`
