# Requirements – Event-Sourced Resource Booking System

**Architecture**: CQRS + Event Sourcing  
**Framework**: Spring Boot 3 + Axon Framework  
**Goal**: Portfolio-quality backend that demonstrates clean CQRS/ES practices with Axon.

This document defines the Minimum Viable Product (MVP) scope and records
explicitly marked post-MVP extensions. Only the MVP requirements are in scope
for the initial implementation.

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

### 1.5 Complex Availability Rules (Post-MVP Extension)

These requirements extend the MVP by allowing resources to define when they can
be booked. Reservation commands and availability queries must respect the
effective bookable windows rather than assuming resources are available 24/7.

#### Availability rule management (write side)

| ID        | Requirement                                                        | Priority | Notes |
|-----------|--------------------------------------------------------------------|----------|-------|
| FR-AV-01  | Define a weekly recurring availability pattern for a resource      | Must     | For example, Mon–Fri 09:00–17:00 and Sat 10:00–14:00; timezone-aware |
| FR-AV-02  | Define one or more one-off blackout or maintenance windows         | Must     | Absolute start/end Instants; reason is optional |
| FR-AV-03  | Define one or more one-off extra availability windows              | Should   | Override or extend the weekly pattern for a specific date range |
| FR-AV-04  | Update an existing weekly pattern                                  | Must     | Emits events; future reservations already booked remain valid |
| FR-AV-05  | Add, remove, or update individual blackout or extra windows        | Must     | |
| FR-AV-06  | Clear all availability rules for a resource, reverting to 24/7     | Should   | Explicit “always available” mode |
| FR-AV-07  | Associate a timezone with the resource’s availability rules       | Must     | Pattern times use this IANA zone ID |

A weekly pattern consists of a day-of-week mapped to a list of start/end
`LocalTime` ranges, a timezone, and optional effective-from/effective-to dates
for seasonal patterns. A blackout or extra window has an absolute start/end
`Instant`, a type (`BLACKOUT` or `EXTRA`), an optional reason/note, and
created-by/created-at metadata.

#### Reservation interaction

| ID        | Requirement                                                        | Priority | Notes |
|-----------|--------------------------------------------------------------------|----------|-------|
| FR-AV-10  | Reject a reservation whose entire time range is outside bookable windows | Must | Core invariant |
| FR-AV-11  | Reject a reservation that only partially overlaps a bookable window | Must | No partial bookings across closed periods |
| FR-AV-12  | Apply existing capacity checks inside bookable windows             | Must     | Evaluate capacity only against concurrent reservations that also fall inside open periods |
| FR-AV-13  | Keep already-confirmed future reservations valid when rules change  | Must     | Soft deactivation; do not auto-cancel |
| FR-AV-14  | Warn or reject when a rule change would leave an existing reservation outside the new windows | Should | Policy is configurable |

#### Availability queries

| ID        | Requirement                                                        | Priority | Notes |
|-----------|--------------------------------------------------------------------|----------|-------|
| FR-AV-20  | Return the current availability rules for a resource               | Must     | Weekly pattern plus blackouts and extra windows |
| FR-AV-21  | Find resources with remaining capacity that are fully available for a given time window | Must | Extends FR-21 |
| FR-AV-22  | Return a calendar-style availability view for a resource over a date range | Must | Per-day or per-slot open/closed state and remaining capacity |
| FR-AV-23  | Return the next N available slots for a resource for a desired duration | Should | Supports finding the next free slot of a requested duration |
| FR-AV-24  | Distinguish bookable hours from occupied hours in daily utilization projections | Should | Extends the utilization endpoint |

#### Supporting capabilities

| ID        | Requirement                                                        | Priority | Notes |
|-----------|--------------------------------------------------------------------|----------|-------|
| FR-AV-30  | Expose all new commands and queries via REST                       | Must     | |
| FR-AV-31  | Update OpenAPI documentation                                       | Must     | |
| FR-AV-32  | Include the acting user identity with commands                     | Must     | Follow the existing `X-User-Id` pattern |
| FR-AV-33  | Provide admin-only endpoints for bulk rule changes if needed       | Should   | |

#### Availability domain invariants

1. A reservation is accepted only when its complete `[start, end)` interval
   lies inside one or more contiguous bookable windows.
2. Capacity is not evaluated outside bookable windows.
3. Changing availability rules never automatically cancels or modifies
   existing confirmed reservations.
4. Blackout windows take precedence over weekly patterns and extra windows.
5. A timezone is mandatory once any non-24/7 rule is present.

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
| NFR-AV-01 | Availability rules are event-sourced; the event store remains the source of truth | Must |
| NFR-AV-02 | Availability and enhanced-utilization projections are rebuildable       | Must |
| NFR-AV-04 | Keep availability logic in a clear package/aggregate boundary so it can evolve independently of the core Resource aggregate if desired | Should |

### 2.2 Reliability & Consistency

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-10  | Optimistic concurrency control on aggregates (Axon default)                 | Must |
| NFR-11  | Projections can be reset and rebuilt from the event store                   | Must |
| NFR-12  | Commands are idempotent where practically possible                          | Should |
| NFR-13  | Failed commands leave the system in a consistent state                      | Must |

### 2.3 Performance Targets

| ID      | Requirement                                                                 | Priority |
|---------|-----------------------------------------------------------------------------|----------|
| NFR-20  | Command handling for a single reservation completes in < 200 ms under normal load (local) | Should |
| NFR-21  | Queries are served from dedicated read models (never by replaying events on every request) | Must |
| NFR-22  | System supports at least a few hundred reservations and tens of resources without noticeable degradation in local testing | Should |
| NFR-AV-03 | Post-MVP: rule evaluation for a single reservation command adds less than 50 ms of local latency | Should |

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
| NFR-AV-05 | Unit tests cover availability rule-combination edge cases, including overnight ranges, timezone transitions, and overlapping blackouts | Must |

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
- Complex availability rules (e.g. business hours, blackout dates); specified as a post-MVP extension in section 1.5
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

## 5. Success Criteria for Complex Availability (Post-MVP)

1. A resource with a Mon–Fri 09:00–17:00 pattern rejects a Saturday booking and
   a Friday 16:00–18:00 booking.
2. Adding a blackout window makes that interval unavailable for new reservations
   while leaving prior bookings untouched.
3. Availability and next-slot queries return correct results from projections.
4. Updating the weekly pattern and rebuilding projections yields identical
   query results.
5. Aggregate unit tests cover the rules and at least one end-to-end integration
   test covers the full flow.

## 6. Suggested Implementation Order for Complex Availability

1. Add the weekly pattern and timezone to Resource, or introduce a dedicated
   Availability aggregate.
2. Add blackout and extra windows.
3. Validate reservation commands against availability rules.
4. Add enhanced availability and next-slot queries.
5. Update utilization projections.

*Document version: 1.0*  
*Based on Axon Framework + Spring Boot implementation.*
