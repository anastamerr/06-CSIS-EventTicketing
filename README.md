# Event Ticketing M3

Team 06 Event Ticketing microservices implementation for M3.

## Modules

- `contracts` - shared Feign clients, DTOs, and RabbitMQ event records.
- `common` - shared security, cache, and observer infrastructure retained from the existing codebase.
- `user-service`
- `event-service`
- `booking-service`
- `ticket-service`
- `sales-service`
- `api-gateway`

## Local Development

Start infrastructure and services with Docker Compose:

```sh
docker compose -f docker-compose.yml up -d --build
```

Run the full Maven reactor test suite:

```sh
./user-service/mvnw test
```

## Kubernetes

Application manifests live under `k8s/` with separate folders for namespaces, secrets, configmaps, PVCs, statefulsets, deployments, services, the API gateway, and monitoring.

Apply order:

```sh
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/deployments/
kubectl apply -f k8s/services/
kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/monitoring/loki/
kubectl apply -f k8s/monitoring/prometheus/
kubectl apply -f k8s/monitoring/grafana/
```
