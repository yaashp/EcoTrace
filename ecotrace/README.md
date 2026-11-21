# EcoTrace

E-waste pickup booking and chain-of-custody tracking for Indian cities.

- Frontend: HTML, CSS and JavaScript (no build step), served by Spring Boot from `src/main/resources/static`.
- Backend: Spring Boot 3 (Java 17), Spring Web, Spring Data JPA, Bean Validation, BCrypt password hashing.
- Database: file-based H2 by default (persists between runs). PostgreSQL is supported through configuration.

> **Prototype.** Demonstration accounts, partners and pickups are sample data and are labelled as such. Partner verification, certificates and outcomes in demo data are not real. Email and SMS notifications are not implemented. EcoRewards is a planned feature only.

## Run it

Requirements: JDK 17 or newer, Maven 3.9 or newer, internet access for the CDN scripts (Leaflet, Chart.js, qrcode.js), the Inter font and OpenStreetMap map tiles.

```bash
mvn spring-boot:run
```

Open http://localhost:8080. In VS Code, install the *Extension Pack for Java*, open the project folder, then run the command above in the terminal or run `EcoTraceApplication`.

Tests: `mvn test`. Package: `mvn package`, then `java -jar target/ecotrace-1.0.0.jar`.

**Upgrading from the first version of this prototype:** the database file name changed (`./data/ecotrace-v2`), so the old `./data/ecotrace.mv.db` is simply ignored. You can delete the `data` folder.

## Configuration

Set as environment variables or in `src/main/resources/application.properties`.

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:h2:file:./data/ecotrace-v2` | JDBC URL. For PostgreSQL, add the `org.postgresql:postgresql` dependency to `pom.xml` and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. |
| `DB_USERNAME`, `DB_PASSWORD` | `sa`, empty | Database credentials |
| `DDL_AUTO` | `update` | Hibernate schema handling (see Database below) |
| `ECOTRACE_DEMO_MODE` | `true` | Creates demonstration data on start, shows the demo banner and the demo sign-in helper, enables the admin reset. **Set to `false` for real use.** |
| `ECOTRACE_DEMO_PASSWORD` | `DemoPass123` | Password given to the demonstration accounts |
| `ECOTRACE_ADMIN_EMAIL`, `ECOTRACE_ADMIN_PASSWORD` | empty | Creates the first administrator on start if no administrator exists. The password must be at least 8 characters with a letter and a number. |
| `ECOTRACE_COOKIE_SECURE` | `false` | Set to `true` when serving over HTTPS so the session cookie is only sent over HTTPS |
| `ECOTRACE_CORS_ORIGINS` | empty | Comma-separated origins, only if the frontend is hosted elsewhere |
| `HANDOVER_CODE_MINUTES` | `30` | How long a customer's collection code stays valid |

Other properties in `application.properties`: `ecotrace.session-hours` (8), `ecotrace.slot-capacity` (8 bookings per slot per day), `ecotrace.max-days-ahead` (60), `ecotrace.csp-enabled` (true).

No secrets are stored in source code or in the frontend. The demo password appears in the demo-mode configuration endpoint only when demo mode is on.

## Database

Tables: `app_user`, `user_session` (hashed tokens), `partner`, `pickup`, `pickup_item`, `pickup_event` (chain of custody), `notification`, `audit_log`, `handover_code` (BCrypt hashes of one-time collection codes, attempt counts, expiry), `handover` (one verified handover per pickup, with its reference, collector, customer and times), `handover_photo` (optional evidence, stored in the database).

Relationships: a pickup belongs to a customer, may have a collector (user), a collection facility and a recycling partner (partners), and has many items and custody events. A recycler account belongs to one recycling partner. Notifications belong to a user. Unique constraints cover user email, pickup reference and session token hash; text lengths and not-null rules come from the entity mappings.

Schema creation uses Hibernate (`ddl-auto=update`), which is reliable for a prototype. For a production deployment, switch to versioned migrations (for example Flyway), set `DDL_AUTO=validate`, and give the application database account insert-only rights on `pickup_event` and `audit_log`.

## Demo data

With demo mode on (the default), start-up creates, if missing: six demonstration accounts, five sample partners and sixteen sample pickups at different stages. Demonstration rows carry a `demo` flag, so they are shown with a "Demo data" or "Sample" label, can be excluded from analytics (untick *Include demonstration data* on the admin overview), and can be reset without touching real accounts or bookings (*Reset demonstration data* on the admin overview, or `POST /api/admin/demo/reset`).

Demonstration accounts (password `DemoPass123` unless changed):

| Role | Email |
|---|---|
| Customer | `customer@demo.example` (also `customer2@demo.example`) |
| Collector | `collector@demo.example` (also `collector2@demo.example`) |
| Recycler (Sample Recycling Partner, Thane) | `recycler@demo.example` |
| Administrator | `admin@demo.example` |

The login page offers buttons that fill these in while demo mode is on. They go through the normal login.

### Demonstration walkthrough

1. **Customer:** log in as the customer, choose *Book a pickup*, add items, an address and a free time slot, review, confirm. Note the reference and QR code on the pickup page.
2. **Administrator:** open *Pickups*, open the new pickup, choose *Confirm and assign a collector*. The customer and collector are notified in the app.
3. **Collector:** open the pickup, *Mark as collected*, then *Record arrival at a collection facility*, then *Record transfer to a recycling partner* (choose the Thane partner).
4. **Recycler:** open *Consignments*, then *Confirm receipt*, *Start processing*, and *Confirm processing outcome* (outcome, measured weight and a processing reference are required).
5. **Administrator:** *Close case*. Open the audit log and the overview to see the counts and charts change.
6. **Customer:** view the pickup, the notifications, and the public tracking page `#/track/<reference>`.

Failure handling: as the collector, report a failed pickup with a reason; as the recycler, reject a consignment with a reason; as an administrator, reschedule or re-route. Try an invalid action (for example a collector confirming recycling) to see it blocked and listed in the audit log.

Ready-made records at each stage: `EW-DEMO0001` to `EW-DEMO0016`.

## Roles and permissions

| Action | Customer | Collector | Recycler | Administrator |
|---|---|---|---|---|
| Book a pickup, see own pickups | Yes | | | |
| Cancel own pickup before collection | Yes | | | Yes |
| Confirm and assign a collector, reschedule | | | | Yes |
| Generate collection code | Own pickup, once a collector is assigned | | | |
| Verify collection code, add handover photo | | Assigned pickups only | | |
| Collected, arrival at facility, transfer, failed pickup | | Assigned pickups only (collected needs a verified handover) | | Facility and transfer only (re-routing) |
| Receipt, processing, outcome, reject | | | Own partner's consignments only | No |
| Close case | | | | Yes |
| See address and contact number | Own pickup | Assigned pickups | No | Yes |
| Manage users, partners, audit log, analytics | | | | Yes |

Allowed status order is defined once in `PickupStatus.allowedNext()`; role permissions in `TransitionPolicy`. Both are checked on the server for every update, together with ownership or assignment and the details each step requires.

## REST API

All paths start with `/api`. State-changing requests need the header `X-Requested-With`.

- Verified handover (signed in): `POST /pickups/{reference}/handover/code` (customer, returns the one-time code once), `POST /pickups/{reference}/handover/verify` (assigned collector, body `{ "code": "123456" }`), `POST /pickups/{reference}/handover/photo` (assigned collector, multipart field `photo`), `GET /pickups/{reference}/handover/photo` (customer, assigned collector or administrator)
- Public: `GET /config`, `GET /meta`, `GET /track/{reference}`, `GET /partners?q=&category=&type=&verification=`, `GET /slots?date=`, `POST /assistant/category`
- Account: `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `PUT /account`, `POST /account/password`
- Pickups (signed in): `POST /pickups` (customer), `GET /pickups?status=&q=&page=&size=`, `GET /pickups/{reference}`, `POST /pickups/{reference}/transition`
- Notifications: `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all`
- Administrator: `GET /admin/metrics?from=&to=&includeDemo=`, `GET /admin/handovers?page=&size=`, `GET|POST /admin/users`, `POST /admin/users/{id}/active`, `GET|POST /admin/partners`, `PUT /admin/partners/{id}`, `POST /admin/partners/{id}/active`, `GET /admin/audit`, `POST /admin/demo/reset`

Errors are JSON: `{ "message": "...", "fieldErrors": { "field": "..." } }`.

## Security notes

- Passwords: BCrypt. Minimum 8 characters with a letter and a number. Never logged.
- Sessions: random 256-bit token in an `HttpOnly`, `SameSite=Lax` cookie (`Secure` when `ECOTRACE_COOKIE_SECURE=true`). Only a SHA-256 hash is stored. Sessions expire after 8 hours and are removed on logout or deactivation.
- Login: five failed attempts for an account lock it for fifteen minutes (in memory). Error messages do not reveal whether an email exists.
- Authorization is enforced on the server in every controller and service. Hiding a button in the page is not relied on. Pickups outside a user's scope return 404.
- CSRF: `SameSite=Lax` cookie plus a required `X-Requested-With` header on state-changing API calls.
- Headers: content security policy limiting scripts to this site and the Leaflet, Chart.js and qrcode.js CDN hosts, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`. Disable the CSP with `ecotrace.csp-enabled=false` if you self-host differently.
- Privacy: public tracking pages and QR codes show status and handovers only. They contain no names, phone numbers, addresses or staff notes. Recyclers never receive contact details.
- Verified handover: the customer's code is random (six digits from a secure random source), stored only as a BCrypt hash, valid for a limited time, usable once, and locked after five wrong guesses. Generating a new code revokes the earlier one. Checks run in order: assignment, format, pickup state, existing handover, open code, expiry, attempt limit, then the code. Each refusal and each successful verification is written to the audit log without the code. Photos are accepted only when their signature bytes are JPEG, PNG or WebP. The collection code is returned only in the response that creates it. It is never logged, never in a notification, and never on a public page.
- Chain of custody: events are append-only at the application level (no setters, insert and read repository, no update or delete endpoints) and carry server timestamps. This is not tamper-proof; see Database for the production step.
- This is a small custom auth layer, kept simple on purpose. For production, use Spring Security with an identity provider.

## What is live, sample and planned

- **Live:** accounts and roles, bookings with time-slot capacity, tracking, QR codes, chain of custody, verified handover with one-time collection codes and optional photo evidence, audit log, in-app notifications, partner directory and map, analytics from stored records.
- **Sample:** demonstration accounts, partners and pickups (always labelled). The category suggestion in the booking form is keyword matching, not a machine-learning model.
- **Planned:** email and SMS delivery, live location, route optimisation, independent verification of recycling partners, and **EcoRewards** (proposed 100 points = ₹5, earned only after verified recycling). EcoRewards has no code, data model or screens beyond an explanatory card.

## Known limitations

- Notifications are in-app only. No external delivery is attempted or claimed.
- Slot capacity is checked before saving, without a database lock, so two simultaneous bookings could briefly exceed it.
- Analytics aggregate in Java over the selected range, which suits a prototype. Use SQL aggregates for large data.
- Login lockout counters and the QR scanner state are not shared across servers. Sessions are stored in the database and survive restarts.
- Outcomes are reported by recycling partners and are not independently audited.
- Pickups collected before verified handover was introduced have no handover record. Resetting demonstration data creates records for the sample pickups.
- The collection code cannot be viewed again after it is generated. If it is lost, generate a new one.
- Attempt counts are stored in the database, so they survive restarts. They are not limited per network address across different pickups.
