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

The application listens on port 8080 and PostgreSQL listens on 5432.
The Compose configuration enables sample resources and future reservations on startup. Sample data is created through Axon commands and is not duplicated when the app restarts.

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

### Direct local run

```bash
export DB_URL=jdbc:postgresql://localhost:5432/axon_resource_booking
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
./mvnw spring-boot:run
```

## API access

All REST endpoints require HTTP Basic authentication. Use the default credentials:

- Username: admin
- Password: admin

OpenAPI documentation is available at:

- Swagger UI: http://localhost:8080/docs
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Health, metrics, and event inspection

- Health: http://localhost:8080/actuator/health
- Prometheus metrics: http://localhost:8080/actuator/prometheus
- Event store inspection: use the reservation event endpoint at /reservations/{reservationId}/events
- Daily resource utilization: GET /resources/{resourceId}/utilization?from=YYYY-MM-DD&to=YYYY-MM-DD

Utilization is projected per UTC day from pending and confirmed reservations. Each daily result includes the reservation count, occupied hours, and the percentage of the resource's capacity-hours used. Date ranges are inclusive and limited to 366 days; days without bookings are returned with zero totals.

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
