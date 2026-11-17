# EcoTrace

**EcoTrace** is a full-stack web application built with **Spring Boot** and vanilla JavaScript for managing, tracking, and auditing responsible waste and recycling pickups. It enforces end-to-end chain-of-custody accountability—from customer pickup scheduling to verified partner handovers with photo evidence and audit logs.

---

## Features

- **Role-Based Portals**: Dedicated workflows for Customers, Pickup Partners/Agents, and Administrators.
- **Pickup Lifecycle Management**: State-machine-enforced status transitions (`TransitionPolicy` & `PickupStatus`) covering booking, scheduling slots, collection, and final disposal/recycling outcomes.
- **Verified Handover & Photo Evidence**:
  - Time-bound handover verification codes (`HandoverCode`) to authenticate physical transfers.
  - Photo upload and retrieval (`HandoverPhoto`) to document item condition and chain-of-custody compliance.
- **Public Tracking**: Unauthenticated tracking portal (`PublicController`) allowing stakeholders to verify pickup timelines and chain-of-custody events.
- **Governance, Metrics & Auditing**:
  - Immutable system activity logging (`AuditLog` & `AuditService`).
  - Operational metrics dashboard (`MetricsService`) and partner directory management[cite: 1].
- **Built-in Assistant & Notifications**: Rule-based in-app guidance (`AssistantService`) and real-time user notifications (`NotificationService`)[cite: 1].

---

## Tech Stack

- **Backend**: Java, Spring Boot (Spring Web, Spring Data JPA)[cite: 1]
- **Database**: H2 Embedded Database (`ecotrace-v2.mv.db`)[cite: 1]
- **Frontend**: HTML5, CSS3, Modular Vanilla JavaScript (`api.js`, `app.js`, role-specific page modules)[cite: 1]
- **Build & Testing**: Maven (`pom.xml`), JUnit / Spring Boot Test (`EcoTraceApiTest`, `HandoverApiTest`)[cite: 1]

---

## Project Structure

```text
ecotrace/
├── pom.xml                                  # Maven dependencies and build configuration
├── data/                                    # Local H2 database storage
└── src/
    ├── main/
    │   ├── java/com/ecotrace/
    │   │   ├── config/                      # Startup seeding and web/CORS configuration
    │   │   ├── domain/                      # JPA entities, enums, and state transition rules
    │   │   ├── dto/                         # Request/response data transfer objects
    │   │   ├── repo/                        # Spring Data repositories
    │   │   ├── service/                     # Core business logic (Auth, Pickup, Handover, Audit)
    │   │   └── web/                         # REST controllers and global exception handling
    │   └── resources/
    │       ├── application.properties       # Server, datasource, and storage configuration
    │       └── static/                      # Single-page frontend (HTML, CSS, JS)
    └── test/                                # Unit and API integration tests
