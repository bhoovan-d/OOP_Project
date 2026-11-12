# I-ITSM: Intelligent IT Service Management & Incident Resolution

CS F213 OOP project, Semester I 2026-27. A full-stack incident management platform: employees report problems, the system **classifies, prioritises, puts them on an SLA clock, detects duplicates and routes them to the best technician automatically**, then tracks them to closure with real-time notifications, escalation and a complete audit trail.

- **Backend:** Java 21 bytecode on Spring Boot 3.5 (Web MVC, Data JPA, Security + JWT, Validation, Actuator), virtual threads
- **Frontend:** React 18 + TypeScript + Vite, Tailwind CSS 4, TanStack Query, Recharts, Server-Sent Events
- **Database:** H2 (zero-setup file DB) by default, PostgreSQL via the `postgres` profile / Docker Compose
- **Tests:** 30 JUnit 5 tests (domain, intelligence, strategies, full HTTP integration)

Full design write-up for the report (architecture, UML, ER, patterns, test cases, technology justification): **[docs/REPORT.md](docs/REPORT.md)**. Visual design system: [DESIGN.md](DESIGN.md).

## Run it

Requirements: JDK 21+ and Node 20+. No database install needed.

```bash
cd frontend && npm install && npm run build
```

```bash
cd backend && ./mvnw spring-boot:run
```

Open http://localhost:8080. On first start the app seeds a demo organisation (12 services, 8 technicians, ~100 historical incidents) into `backend/data/`. Delete that folder to reseed.

**Development mode** (hot reload): run the backend as above, then `cd frontend && npm run dev` and open http://localhost:5173 (API calls proxy to :8080).

**Docker + PostgreSQL:** `docker compose up --build`

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Employee | rohan@itsm.local (also sneha, aditya, meera, karan, pooja) | `User@123` |
| Technician | neha@itsm.local (also priya, arjun, rahul, kavya, vikram, ananya, sameer) | `Tech@123` |
| Admin | admin@itsm.local | `Admin@123` |
| IT manager | manager@itsm.local | `Admin@123` |

## Tests and load test

```bash
cd backend && ./mvnw test
```

```bash
cd backend && java loadtest/LoadTest.java 20000 200
```

Measured on a laptop (H2, single instance): **20,000 authenticated API requests at 200 concurrent clients: ~1,460 req/s, 0 failures, p95 435 ms.**

## Feature map (spec → where)

| Requirement | Implementation |
|---|---|
| 3 user roles, register/login | `User` → `Employee`/`Technician`/`Administrator`, JWT auth, `/register` page |
| Incident CRUD + lifecycle | `Incident` aggregate + `IncidentStatus` state machine (10 states, illegal moves rejected) |
| Priority (impact × urgency × criticality × users) | `PriorityEngine`, explanation shown in UI |
| ≥ 2 assignment strategies | Skill-based, Load-balanced, Round-robin, Smart (weighted); switchable at runtime |
| SLA calc, deadlines, warnings, violations | `SlaPolicy` per service, `SlaMonitor` background job, pause while waiting on reporter |
| ≥ 2 notification mechanisms | In-app (live SSE), Email (simulated SMTP outbox), SMS (adapter over gateway client) |
| Audit trail | Observer writes every event (actor, from/to state, description) in the same transaction |
| ≥ 5 search criteria | 13: text/number, status, priority, service, category, technician, reporter, SLA status, breached, open, unassigned, date range |
| ≥ 2 intelligent features | **7**: duplicate detection, correlation/major-incident detection, auto-escalation, smart technician recommendation, predictive SLA risk, auto-categorisation, suggested resolutions |
| Reports/dashboards | Operations console: KPIs, service health, SLA risk, trends, workload, MTTR, category & service analysis, CSV export |
| Innovation | Real-time push (SSE), live triage while typing, predictive SLA risk, event-driven architecture, Docker/CI |
