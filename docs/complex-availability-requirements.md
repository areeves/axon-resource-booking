# Requirements – Complex Availability Rules

**Project**: Axon Resource Booking  
**Feature**: Complex Availability Rules (post-MVP extension)  
**Architecture**: CQRS + Event Sourcing (Axon Framework + Spring Boot)  
**Document version**: 1.0  
**Status**: Draft

---

## Goal

Allow resources to declare when they are bookable (business hours, recurring patterns, one-off blackouts/maintenance windows) so that reservation commands and availability queries respect real-world constraints instead of assuming 24/7 availability.

**Architecture notes**

- Keep all business invariants inside aggregates (or a dedicated Availability aggregate).
- Availability state is event-sourced; projections remain eventually consistent and rebuildable.
- Existing capacity checks continue to apply *only* inside the bookable windows.

---

## 1. Functional Requirements

### 1.1 Availability Rule Management (Write Side)

| ID       | Requirement                                                              | Priority | Notes |
|----------|--------------------------------------------------------------------------|----------|-------|
| FR-AV-01 | Define a weekly recurring availability pattern for a resource            | Must     | e.g. Mon–Fri 09:00–17:00, Sat 10:00–14:00 (timezone-aware) |
| FR-AV-02 | Define one or more one-off blackout / maintenance windows                | Must     | Absolute start/end Instants; reason optional |
| FR-AV-03 | Define one or more one-off extra availability windows                    | Should   | Override or extend the weekly pattern for a specific date range |
| FR-AV-04 | Update an existing weekly pattern                                        | Must     | Emits events; future reservations already booked remain valid |
| FR-AV-05 | Add / remove / update individual blackout or extra windows               | Must     | |
| FR-AV-06 | Clear all availability rules for a resource (revert to 24/7)             | Should   | Explicit “always available” mode |
| FR-AV-07 | Associate a timezone with the resource’s availability rules              | Must     | All pattern times interpreted in this zone; stored as IANA zone ID |

**Minimum attributes for a weekly pattern**

- Day-of-week → list of time ranges (start/end as `LocalTime`)
- Timezone
- Effective-from / effective-to (optional, for seasonal patterns)

**Minimum attributes for a blackout / extra window**

- Start / end (`Instant`)
- Type (`BLACKOUT` | `EXTRA`)
- Reason / note (optional)
- Created-by / created-at

### 1.2 Reservation Interaction

| ID       | Requirement                                                                 | Priority | Notes |
|----------|-----------------------------------------------------------------------------|----------|-------|
| FR-AV-10 | Reject a reservation whose entire time range falls outside all bookable windows | Must  | Core invariant |
| FR-AV-11 | Reject a reservation that only partially overlaps a bookable window         | Must     | No partial bookings across closed periods |
| FR-AV-12 | Existing capacity checks still apply inside the bookable windows            | Must     | Capacity is evaluated only against concurrent reservations that also fall inside open periods |
| FR-AV-13 | When availability rules change, already-confirmed future reservations remain valid | Must | Soft deactivation style – do not auto-cancel |
| FR-AV-14 | Optionally warn (or reject) when a rule change would leave an existing reservation outside the new windows | Should | Configurable policy |

### 1.3 Queries (Read Side)

| ID       | Requirement                                                                 | Priority | Notes |
|----------|-----------------------------------------------------------------------------|----------|-------|
| FR-AV-20 | Return the current availability rules for a resource                        | Must     | Weekly pattern + list of blackouts/extras |
| FR-AV-21 | Find resources that have remaining capacity *and* are fully available for a given time window | Must | Extends existing FR-21 |
| FR-AV-22 | Return a calendar-style availability view for a resource over a date range  | Must     | Per-day or per-slot open/closed + remaining capacity |
| FR-AV-23 | Return the next N available slots for a resource (given desired duration)   | Should   | Useful for “find me the next free 2-hour slot” |
| FR-AV-24 | Daily utilization projection distinguishes bookable hours vs occupied hours | Should   | Extend existing utilization endpoint |

### 1.4 Supporting Capabilities

| ID       | Requirement                                                      | Priority | Notes |
|----------|------------------------------------------------------------------|----------|-------|
| FR-AV-30 | All new commands and queries exposed via REST                    | Must     | |
| FR-AV-31 | OpenAPI documentation updated                                    | Must     | |
| FR-AV-32 | Commands carry the acting user identity (existing X-User-Id pattern) | Must  | |
| FR-AV-33 | Admin-only endpoints for bulk rule changes if needed             | Should   | |

---

## 2. Domain Invariants (enforced by Aggregate)

1. A reservation may only be accepted if its complete `[start, end)` interval lies inside one or more contiguous bookable windows.
2. Capacity is never evaluated outside bookable windows.
3. Changing availability rules never automatically cancels or modifies existing confirmed reservations.
4. Blackout windows take precedence over weekly patterns and extra windows.
5. Timezone is mandatory once any non-24/7 rule is present.

---

## 3. Non-Functional / Design Constraints

| ID         | Requirement                                                                 | Priority |
|------------|-----------------------------------------------------------------------------|----------|
| NFR-AV-01  | Availability rules are event-sourced (source of truth remains the event store) | Must  |
| NFR-AV-02  | Projections for availability and enhanced utilization are rebuildable       | Must     |
| NFR-AV-03  | Rule evaluation for a single reservation command stays fast (< 50 ms additional latency locally) | Should |
| NFR-AV-04  | Clear package / aggregate boundary so availability logic can evolve independently of the core Resource aggregate if desired | Should |
| NFR-AV-05  | Unit tests cover all rule-combination edge cases (overnight ranges, timezone transitions, overlapping blackouts, etc.) | Must |

---

## 4. Explicitly Out of Scope (for this feature)

- Multi-resource “and” constraints (e.g. room + projector must both be free)
- Dynamic pricing or different capacity per time-of-day
- User-specific availability overrides
- Automatic rescheduling of existing reservations when rules change
- Integration with external calendar systems for rule import (can be a later feature)

---

## 5. Success Criteria

1. A resource with a Mon–Fri 09:00–17:00 pattern correctly rejects a Saturday booking and a 16:00–18:00 Friday booking.
2. Adding a blackout window immediately makes that interval unavailable for new reservations while leaving prior bookings untouched.
3. Availability and “next available slot” queries return correct results from projections.
4. Changing the weekly pattern and then rebuilding projections yields identical query results.
5. All new behaviour is covered by aggregate unit tests and at least one end-to-end integration test.

---

## 6. Suggested Implementation Order

1. Weekly pattern + timezone on Resource (or new Availability aggregate)
2. Blackout / extra windows
3. Reservation command validation against rules
4. Enhanced availability & next-slot queries
5. Utilization projection updates

---

*This document is intended as a living checklist. Copy requirement IDs into tickets or merge into the main `REQUIREMENTS.md` when development begins.*
