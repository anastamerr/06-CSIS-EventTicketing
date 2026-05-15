# S3-C Booking Infra Handoff

Team member: Abdelrahman Elzeiny  
Student ID: 55-7406  
GitHub: Elzeiny23  
Service: booking-service  
Slice: S3-C - Cross-Cutting Infra

## API Gateway Scope

Booking traffic is routed by the gateway route:

- route id `booking-service`
- upstream `http://booking-service:8080`
- path predicate `/api/bookings/**`

This covers the booking read, confirmation, completion, and cancellation paths:

- `GET /api/bookings/{id}`
- `GET /api/bookings/user/{userId}/summary`
- `GET /api/bookings/user/{userId}/active-count`
- `GET /api/bookings/user/{userId}/count`
- `GET /api/bookings/user/{userId}/total`
- `GET /api/bookings/event/{eventId}/revenue`
- `GET /api/bookings/event/{eventId}/active-count`
- `PUT /api/bookings/{bookingId}/confirm?eventId={eventId}`
- `PUT /api/bookings/{id}/complete`
- `PUT /api/bookings/{id}/cancel`

## Messaging Contract Check

Booking-service now uses shared contract constants from `contracts.messaging.EventTicketingMessagingContracts`.

- publishes to exchange `booking.events`
- publishes routing keys `booking.placed`, `booking.completed`, `booking.cancelled`
- consumes from exchanges `payment.events` and `ticket.events`
- queue `booking.saga-feedback`
- DLQ `booking.saga-feedback.dlq`
- DLX `booking.saga-feedback.dlx`
- bound routing keys `payment.initiated`, `payment.completed`, `payment.failed`, `payment.refunded`, `ticket.issued`

## Kubernetes Scope

RabbitMQ infra is owned by this slice:

- `k8s/statefulsets/rabbitmq-statefulset.yaml`
- `k8s/services/rabbitmq-svc.yaml`
- `k8s/pvcs/rabbitmq-pvc.yaml`

Booking-service connects with `SPRING_RABBITMQ_HOST=rabbitmq` and AMQP port `5672`.

## Observability Scope

Prometheus has a booking scrape job:

- job name `booking-service`
- target `booking-service.eventticketing.svc.cluster.local:8080`
- metrics path `/actuator/prometheus`

The booking dashboard aggregation reference is `k8s/monitoring/grafana/dashboards/booking-dashboard.json`, with PromQL panels for request rate, p95 latency, and heap usage plus LogQL panels for errors, Feign activity, and booking event logs.

## Verification Handoff

After deployment:

```sh
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/rabbitmq-statefulset.yaml
kubectl apply -f k8s/services/rabbitmq-svc.yaml
kubectl apply -f k8s/configmaps/gateway-configmap.yaml
kubectl apply -f k8s/deployments/booking-service-deployment.yaml
kubectl apply -f k8s/services/booking-service-svc.yaml
kubectl apply -f k8s/api-gateway/
```

```sh
kubectl wait --for=condition=ready pod -l app=rabbitmq -n eventticketing --timeout=180s
kubectl wait --for=condition=ready pod -l app=booking-service -n eventticketing --timeout=180s
kubectl wait --for=condition=ready pod -l app=api-gateway -n eventticketing --timeout=180s
```

Use the gateway NodePort for endpoint checks:

```sh
GATEWAY_URL="http://$(minikube ip):30080"
curl -i "$GATEWAY_URL/api/bookings/1" -H "Authorization: Bearer $TOKEN"
curl -i -X PUT "$GATEWAY_URL/api/bookings/1/confirm?eventId=10" -H "Authorization: Bearer $TOKEN"
curl -i -X PUT "$GATEWAY_URL/api/bookings/1/complete" -H "Authorization: Bearer $TOKEN"
curl -i -X PUT "$GATEWAY_URL/api/bookings/1/cancel" -H "Authorization: Bearer $TOKEN"
```

Verify RabbitMQ connectivity from booking-service:

```sh
kubectl exec -n eventticketing deploy/booking-service -- printenv SPRING_RABBITMQ_HOST
kubectl logs -n eventticketing deploy/booking-service | grep -E "booking\\.(placed|completed|cancelled)|payment\\.|ticket\\.issued|Rabbit"
kubectl exec -n eventticketing statefulset/rabbitmq -- rabbitmq-diagnostics listeners
```
