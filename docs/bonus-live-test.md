# Bonus Live Test Runbook

## CI/CD

The workflow in `.github/workflows/ci.yml` runs on `feat/**`, `main`, and pull requests to `main`.

- Feature branches: Maven reactor tests and Docker image builds.
- Main branch: Maven reactor tests, Docker image builds, and pushes `latest` plus SHA tags to GHCR.

## Ingress

```powershell
minikube addons enable ingress
kubectl apply -f k8s/api-gateway/gateway-ingress.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=180s
```

Add a hosts entry:

```text
<minikube-ip> eventticketing.local
```

Then verify:

```powershell
curl -H "Host: eventticketing.local" http://$(minikube ip)/actuator/health
```

## HPA

```powershell
minikube addons enable metrics-server
kubectl apply -f k8s/hpa/booking-service-hpa.yaml
kubectl get hpa booking-service -n eventticketing
```

Generate authenticated traffic against the gateway booking endpoints and watch:

```powershell
kubectl get hpa booking-service -n eventticketing --watch
```

## Circuit Breaker

Feign circuit breakers are enabled in all Feign-using services:

```yaml
feign:
  circuitbreaker:
    enabled: true
```

Resilience4j uses a count-based sliding window of 5 calls, opens at a 50% failure rate, waits 5 seconds, then probes half-open recovery. Existing service-level Feign error handling supplies fallback behavior: empty summaries, zero counts, `404` for missing downstream resources, or `503` when the requested workflow cannot continue safely.

To demonstrate:

1. Scale a downstream dependency to zero, for example `kubectl scale deployment ticket-service -n eventticketing --replicas=0`.
2. Call a booking endpoint that requires ticket-service several times.
3. Observe fallback/error behavior and circuit breaker metrics under `/actuator/prometheus`.
4. Restore the downstream deployment and repeat after `wait-duration-in-open-state`.

## One-Command Helper

From repo root:

```powershell
.\scripts\live-test-bonus.ps1
```
