# S5-C Sales Infra Handoff

## API Gateway Scope

Sales traffic is routed by the gateway ConfigMap route:

- `Path=/api/sales/**`
- upstream `http://sales-service:8080`

This covers the current sales API surface:

- summary: `GET /api/sales/user/{userId}/summary`
- payment: `POST /api/sales/booking/{bookingId}`
- promotions: `/api/sales/promotions/**`, `/api/sales/discounts/**`, `/api/sales/{saleId}/promotions/{promotionId}`
- refund: `PUT /api/sales/{id}/refund`, `POST /api/sales/{id}/refund-window-policy`
- reports and analytics: `GET /api/sales/reports/revenue`, `GET /api/sales/analytics/tier`, `GET /api/sales/{saleId}/audit-trail`

## Messaging Contract Check

Sales declares the shared M3 exchange and queue names:

- publishes to `payment.events`
- consumes from `booking.events`
- queue `payment.saga-listener`
- DLQ `payment.saga-listener.dlq`
- bound routing keys `booking.completed` and `booking.cancelled`
- payment routing keys `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`

## Observability Scope

Sales exposes `/actuator/prometheus` and the Prometheus ConfigMap includes:

- job name `sales-service`
- target `sales-service.eventticketing.svc.cluster.local:8080`

Grafana provisioning includes Prometheus and Loki datasources plus the dashboards provider. The sales dashboard contains:

- LogQL: sales error rate, correlation trace, RabbitMQ event audit
- PromQL: request rate, p95 latency, JVM memory aggregation

## Final Integration Runbook

Run after all service slices merge and the cluster is deployed.

1. Apply infrastructure:

```powershell
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

2. Confirm readiness:

```powershell
kubectl get pods -n eventticketing
kubectl get pods -n monitoring
kubectl wait --for=condition=ready pod -l app=sales-service -n eventticketing --timeout=180s
kubectl wait --for=condition=ready pod -l app=sales-postgres -n eventticketing --timeout=180s
kubectl wait --for=condition=ready pod -l app=rabbitmq -n eventticketing --timeout=180s
kubectl wait --for=condition=ready pod -l app=prometheus -n monitoring --timeout=180s
kubectl wait --for=condition=ready pod -l app=grafana -n monitoring --timeout=180s
```

3. Scenario A, happy path:

- Seed user `1` ACTIVE, event `5` ONGOING, booking `10` CHECKED_IN with total `800`, and three USED tickets for booking `10`.
- `PUT http://$(minikube ip):30080/api/bookings/10/complete`
- Verify booking reaches `PAYMENT_PENDING` and sales-postgres has a PENDING sale for booking `10`.
- `POST http://$(minikube ip):30080/api/sales/booking/10` with `{"method":"CREDIT_CARD","cardLastFour":"4242"}`
- Verify `payment.completed` is published and booking reaches `PAID`.

4. Scenario B, payment failure compensation:

- Repeat Scenario A until `PAYMENT_PENDING`.
- Submit invalid payment data to `POST /api/sales/booking/10`.
- Verify `payment.failed`, `booking.cancelled`, cancelled tickets, refunded sale, reversed stats, and booking `REFUNDED`.

5. Scenario C, pre-check failure:

- Seed booking `10` CHECKED_IN with zero USED tickets.
- `PUT http://$(minikube ip):30080/api/bookings/10/complete`
- Verify HTTP 400, no `booking.completed` event, and booking remains `CHECKED_IN`.

6. Observability signoff:

- Open Grafana at `http://$(minikube ip):30030`.
- Confirm Prometheus and Loki datasources are healthy.
- Confirm the Sales Service dashboard shows HTTP metrics and sales log/event panels after running A/B/C.
