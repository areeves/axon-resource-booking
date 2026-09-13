# Requirements – Event-Sourced Resource Booking System

**Architecture**: CQRS + Event Sourcing  
**Framework**: Spring Boot 3 + Axon Framework  
**Goal**: Portfolio-quality backend that demonstrates clean CQRS/ES practices with Axon.

This document defines the Minimum Viable Product (MVP) scope.  
All requirements below are in scope for the initial implementation.

---

## 1. Functional Requirements (FRs)

### 1.1 Resource Management

| ID     | Requirement                                                                 | Priority | Notes |
|--------|-----------------------------------------------------------------------------|----------|-------|
| FR-01  | Create a new resource                                                       | Must     | Name, description, capacity (≥ 1), location |
| FR-02  | Update an existing resource’s details                                       | Must     | Name, description, location. Capacity changes must be handled carefully (see domain rules) |
| FR-03  | Deactivate a resource                                                       | Must     | Soft deactivation. Existing future reservations remain valid; new reservations are rejected |
| FR-04  | Reactivate a previously deactivated resource                                | Must     | |
| FR-05  | Retrieve a single resource by its ID                                        | Must     | |
| FR-06  | List all active resources                                                   | Must     | |

**Resource attributes (minimum)**
- `resourceId` (UUID)
- `name` (required, unique)
- `description` (optional)
- `capacity` (integer ≥ 1)
- `location` (string)
- `status` (ACTIVE / INACTIVE)
- `createdAt` / `lastModifiedAt`

### 1.2 Reservation Lifecycle (Write Side)

| ID     | Requirement                                                                 | Priority | Notes |
|--------|-----------------------------------------------------------------------------|----------|-------|
| FR-10  | Reserve a resource for a time range on behalf of a user                     | Must     | Command: `ReserveResource` |
| FR-11  | Cancel an existing reservation                                              | Must     | Only the owning user (or admin) may cancel |
| FR-12  | Confirm a reservation                                                       | Should   | Simple state transition (PENDING → CONFIRMED). Can be automatic on creation for MVP |
| FR-13  | Reject overlapping reservations that would exceed the resource’s capacity   | Must     | Core invariant enforced inside the aggregate |
| FR-14  | Validate time range                                                         | Must     | `end` must be after `start`; reservation must not start in the past (configurable tolerance allowed) |

**Reservation attributes (minimum)**
- `reservationId` (UUID)
- `resourceId`
- `userId`
- `start` / `end` (Instant or OffsetDateTime)
- `status` (PENDING, CONFIRMED, CANCELLED)
- `createdAt` / `lastModifiedAt`

**Domain invariants (must be enforced by the Aggregate)**
- A resource cannot be over-booked at any point in time (capacity must never be exceeded).
- A cancelled reservation frees its capacity immediately.
- Reservations on an INACTIVE resource are rejected.

### 1.3 Queries (Read Side)

| ID     | Requirement                                                                 | Priority | Notes |
|--------|-----------------------------------------------------------------------------|----------|-------|
| FR-20  | List all active resources                                                   | Must     | |
| FR-21  | Find available resources for a given time window                            | Must     | Returns resources that still have remaining capacity in the requested interval |
| FR-22  | List all reservations for a specific user                                   | Must     | |
| FR-23  | List all reservations for a specific resource                               | Must     | |
| FR-24  | Retrieve the full event history of a reservation                            | Must     | Useful for debugging and demonstration of event sourcing |
| FR-25  | Retrieve current details of a single reservation                            | Must     | |

### 1.4 Supporting Capabilities

| ID     | Requirement                                                                 | Priority | Notes |
|--------|-----------------------------------------------------------------------------|----------|-------|
| FR-30  | All commands and queries are exposed via REST API                           | Must     | |
| FR-31  | OpenAPI / Swagger documentation is available                                | Must     | |
| FR-32  | Unique identifiers are UUIDs                                                | Must     | |
| FR-33  | User identity is passed with every command (simple header or principal)     | Must     | Full authentication can be basic for MVP |

---

## 2. Non-Functional Requirements (NFRs)

### 2.1 Architecture & Design

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-01  | Strict CQRS separation: commands never return business data; queries never change state | Must |
| NFR-02  | Event Store is the single source of truth                                   | Must |
| NFR-03  | All business invariants are enforced inside Aggregates                      | Must |
| NFR-04  | Read models (projections) are eventually consistent and rebuildable from the event stream | Must |
| NFR-05  | Use Axon Framework for command bus, event bus, event sourcing repositories, and projections | Must |
| NFR-06  | Clear package separation between write side, read side, and shared kernel   | Must |

### 2.2 Reliability & Consistency

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-10  | Optimistic concurrency control on aggregates (Axon default)                 | Must |
| NFR-11  | Projections can be reset and rebuilt from the event store                   | Must |
| NFR-12  | Commands are idempotent where practically possible                          | Should |
| NFR-13  | Failed commands leave the system in a consistent state                      | Must |

### 2.3 Performance (MVP targets)

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-20  | Command handling for a single reservation completes in < 200 ms under normal load (local) | Should |
| NFR-21  | Queries are served from dedicated read models (never by replaying events on every request) | Must |
| NFR-22  | System supports at least a few hundred reservations and tens of resources without noticeable degradation in local testing | Should |

### 2.4 Observability & Operability

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-30  | Structured logging of important commands and domain events                  | Must |
| NFR-31  | Spring Boot Actuator health endpoint                                        | Must |
| NFR-32  | Ability to inspect the raw event stream for any aggregate via an API or Axon tools | Must |
| NFR-33  | Basic metrics (command counts, event counts) via Actuator / Micrometer      | Should |

### 2.5 Quality & Testing

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-40  | Unit tests for all Aggregate behaviour and invariants                       | Must |
| NFR-41  | Integration tests covering Command → Event → Projection flow                | Must |
| NFR-42  | Testcontainers used for PostgreSQL in integration tests                     | Must |
| NFR-43  | Meaningful test coverage of the write-side domain logic                     | Must |

### 2.6 Security (MVP level)

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-50  | Simple authentication mechanism (Basic Auth or API key) is sufficient       | Must |
| NFR-51  | Every command carries a user identity                                       | Must |
| NFR-52  | A user can only cancel their own reservations (unless acting as admin)      | Must |

### 2.7 Documentation & Developer Experience

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-60  | README contains architecture overview, how to run, and key design decisions | Must |
| NFR-61  | OpenAPI documentation covers all endpoints                                  | Must |
| NFR-62  | Docker Compose file provided for local PostgreSQL + application             | Must |
| NFR-63  | Clear instructions for rebuilding projections                               | Should |

---

## 3. Out of Scope for MVP

- Recurring / repeating reservations
- Waitlists
- Approval workflows
- Multi-tenancy
- Advanced authorization / roles beyond simple user vs admin
- Real-time notifications (WebSocket, email, etc.)
- Complex availability rules (e.g. business hours, blackout dates)
- UI of any kind
- Horizontal scaling / distributed Axon setup

---

## 4. Success Criteria for MVP

The project is considered MVP-complete when:

1. A resource can be created and reserved without violating capacity.
2. Overlapping reservations that would exceed capacity are correctly rejected.
3. Cancellations free capacity and are reflected in availability queries.
4. All listed queries return correct data from projections.
5. The event stream of any reservation can be retrieved.
6. Projections can be rebuilt from the event store.
7. The application starts with Docker Compose and has passing integration tests.
8. README and OpenAPI documentation are clear enough for another developer to understand and run the project.

---

*Document version: 1.0*  
*Based on Axon Framework + Spring Boot implementation.*
