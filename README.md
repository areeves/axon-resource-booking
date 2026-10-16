# Axon Resource Booking

A Spring Boot application demonstrating CQRS + event sourcing with Axon for resource booking and reservation management.

## Architecture overview

- Write side: resource and reservation commands are handled by aggregates.
- Event store: PostgreSQL-backed Axon event store is the source of truth.
- Read side: event handlers project state into JPA read models used by the REST queries.
- API layer: REST endpoints expose commands and queries for resource and reservation operations.

## Requirements covered

- CQRS separation between command and query flows
- Aggregate-enforced business invariants
- Event-sourced history and rebuildable projections
- Operational readiness with Actuator health and metrics
- Basic security using HTTP Basic auth for the API surface

## Local run

### With Docker Compose

```bash
docker compose up --build
```

Docker Compose runs the Vue console with Vite and the Spring Boot API in separate containers. Open the console at http://localhost; edits to Vue files on the host are applied by Vite's hot-module replacement. Java and resource changes under `src/main` are recompiled automatically, and Spring DevTools restarts the API. The API is also available directly at http://localhost:8080, and PostgreSQL listens on port 5432.
The Compose configuration enables sample resources and future reservations on startup. Sample data is created through Axon commands and is not duplicated when the app restarts.

When running Vite outside Docker, set `VITE_API_PROXY_TARGET` if the API is not at `http://localhost:8080`. Docker Compose sets this to `http://api:8080` for its container network.

To rebuild the database from scratch and repopulate the sample data:

```bash
docker compose down -v
docker compose up --build
```

### Sample data for a direct local run

Set `APP_SAMPLE_DATA_ENABLED=true` when starting the application:

```bash
APP_SAMPLE_DATA_ENABLED=true ./mvnw spring-boot:run
```

### Run the API and frontend separately

```bash
export DB_URL=jdbc:postgresql://localhost:5432/axon_resource_booking
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
./mvnw spring-boot:run
```

In another terminal, start the frontend:

```bash
cd frontend
npm ci
npm run dev
```

Open the Vite URL printed in the frontend terminal. The frontend proxies API requests to `http://localhost:8080` by default; set `VITE_API_PROXY_TARGET` if the API runs elsewhere. Spring serves the API, but does not serve the web console.

## API access

All REST endpoints require HTTP Basic authentication. Use the default credentials:

- Username: admin
- Password: admin

A non-admin demo account is also available for owner-scoped reservation actions:

- Username / actor UUID: `00000000-0000-0000-0000-000000000001`
- Password: `user`

Non-admin requests must send an `X-User-Id` matching the authenticated account. Configure the demo actor and password with `APP_SECURITY_USER_ID` and `APP_SECURITY_USER_PASSWORD`. Administrators may act as another supplied actor UUID.

Availability rule changes that would put existing pending or confirmed reservations outside bookable hours are rejected by default. Set `APP_AVAILABILITY_REJECT_CONFLICTING_RULE_UPDATES=false` to allow such changes; the aggregate logs the affected reservation IDs. Existing reservations are never automatically changed or cancelled.

## Web console

Open the frontend URL (http://localhost with Docker Compose, or the Vite URL printed by `npm run dev`) to manage resources and reservations, inspect reservation events, view daily utilization, and rebuild projections. The console asks for API credentials and an actor UUID; the password is kept only in the current page session. The Spring API at http://localhost:8080 does not serve the web console.

The Vue console is built independently by Vite and served by the frontend Nginx container in production. Maven builds only the Spring Boot API.

OpenAPI documentation is available at:

- Swagger UI: http://localhost:8080/docs
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Health, metrics, and event inspection

- Health: http://localhost:8080/actuator/health
- Prometheus metrics: http://localhost:8080/actuator/prometheus
- Reservation history: `/reservations/{reservationId}/events`
- Full aggregate event stream (admin): `GET /admin/events/{aggregateId}`
- Daily resource utilization: GET /resources/{resourceId}/utilization?from=YYYY-MM-DD&to=YYYY-MM-DD
- Availability calendar: `GET /resources/{resourceId}/availability/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD&slotMinutes=60`
- Next available slots: `GET /resources/{resourceId}/availability/next-slots?after=ISO-8601&durationMinutes=60&count=5`

Utilization is projected from pending and confirmed reservations. Each daily result includes the reservation count, scheduled bookable hours, occupied hours, and the percentage of scheduled capacity-hours used. Dates are grouped in UTC; bookable hours use the resource's configured availability timezone. Date ranges are inclusive and limited to 366 days; days without bookings are returned with zero totals.

## Rebuilding projections

An administrator can rebuild the resource, reservation, and utilization read models from the event store with:

```bash
curl -u admin:admin -X POST http://localhost:8080/admin/projections/rebuild
```

The endpoint clears the projection tables and resets the Axon tracking processor. It returns `202 Accepted`; query results become available again after replay completes.

## Key design decisions

1. Business rules stay inside aggregates so the event store remains the canonical truth.
2. Projections are intentionally separate from command execution to keep the query side eventually consistent.
3. User identity is included with commands via the X-User-Id header, while API auth is handled through HTTP Basic authentication.
