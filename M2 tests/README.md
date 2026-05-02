# Event Ticketing M2 Tests

This folder contains the official Event Ticketing M2 scenario document and an executable HTTP test runner for the cases that can be validated through public service APIs.

## Run

Start the stack first:

```powershell
docker compose up -d --build
```

Then run:

```powershell
cd "M2 tests"
npm test
```

The runner does not hard-code generated test data. It creates nonce-based users, phones, and payloads per run. Service URLs are configurable:

```powershell
$env:USER_SERVICE_URL="http://localhost:8081"
$env:EVENT_SERVICE_URL="http://localhost:8082"
npm test
```

Defaults match `docker-compose.yaml`: user service on `8081` and event service on `8082`.
