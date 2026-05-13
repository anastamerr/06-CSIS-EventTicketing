# Summary

Implements the M3 S1-A Read & DB slice for `user-service`.

- Refactors S1-F3, S1-F4, S1-F6, and S1-F9 away from direct `bookings` SQL access and onto the shared `BookingServiceClient` Feign contract.
- Enables user-service Feign support, Feign correlation ID forwarding, RabbitMQ publishing for `user.registered` and `user.deactivated`, JSON AMQP conversion, and Loki4J logging.
- Isolates user-service PostgreSQL to `user-postgres:5432/etdb-users` and removes the old local `bookings` schema initialization from user-service.
- Adds user-owned Kubernetes manifests for user Postgres and service configuration.
- Adds user Grafana dashboard panels for LogQL and PromQL observability.
- Extends the booking contract and booking-service count endpoint to support `GET /api/bookings/user/{userId}/count?status=COMPLETED` for S1-F9.
- Fixes two pre-existing full-suite failures found during readiness checks:
  - ticket analytics now returns an empty status map when no tickets match.
  - sales refund-window cache invalidation now deletes the exact S5-F11 sale key expected by the service contract.

# Key Files / Areas Changed

- `user-service`
  - `UserServiceApplication.java`: enables Feign clients.
  - `UserService.java`: replaces cross-service booking reads with Feign calls, wraps Feign failures, logs Feign outcomes, publishes user events.
  - `UserRepository.java`: removes native SQL that joined/read `bookings`.
  - `ExternalSchemaInitializer.java`: removes user-service creation/migration of a local `bookings` table.
  - `config/FeignCorrelationConfig.java`: forwards `X-Correlation-ID`.
  - `messaging/UserEventConfig.java` and `UserEventPublisher.java`: declares `user.events`, sends JSON events, sets routing/user MDC for event logs.
  - `application.yml` and `logback-spring.xml`: user DB isolation, Feign URL, RabbitMQ, actuator/Prometheus, Loki4J.
  - `UserServiceTest.java` and `UserEventPublisherTest.java`: updated coverage for Feign-based reads and event publishing.
- `contracts` / `booking-service`
  - `BookingServiceClient.java`, `BookingController.java`, `BookingService.java`: optional `status` filter for user booking counts.
- `k8s`
  - user Postgres Secret, StatefulSet, headless Service, PVC, and user-service ConfigMap.
  - `user-dashboard.json`: user LogQL and PromQL panels.
- Readiness fixes
  - `ticket-service/TicketService.java`
  - `sales-service/TicketSaleService.java`

# Testing Performed

- Read implementation requirements in `M3.md` for:
  - DB isolation deliverables.
  - OpenFeign setup and correlation propagation.
  - S1-F3, S1-F4, S1-F6, S1-F9 behavior.
  - S1 deliverables.
  - Loki4J/Grafana observability requirements.
- Ran focused S1-A and dependent reactor tests:
  - `mvn -pl contracts,common,user-service,booking-service test`
- Ran full repository tests:
  - `mvn test`
- Ran package/build check:
  - `mvn package -DskipTests`
- Ran whitespace check:
  - `git diff --check`
- Parsed all Grafana dashboard JSON files with Python `json.loads`.
- Verified no remaining direct user-service SQL references to the `bookings` table with `rg`.

# Risks / Assumptions / Follow-ups

- `yamllint`, `kubeconform`, `kubectl`, and `gh` are not installed in this environment, so Kubernetes schema validation and opening the PR from the CLI could not be performed locally.
- `Jackson2JsonMessageConverter` is already used by booking-service and was mirrored here for consistency, but it is marked deprecated by the current dependency set. A future cleanup should migrate all AMQP configs to the replacement converter together.
- Full cluster validation still needs to be run in Minikube with RabbitMQ, Loki, Prometheus, Grafana, and the service pods.
