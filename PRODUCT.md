# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Delegated: Spring Boot 3.5 (Java 21 bytecode, virtual threads) REST API + React/Vite/TypeScript SPA served from the same jar. H2 file DB by default, PostgreSQL profile for production.

## Users

- **Employees** (staff/students) who hit an IT problem and need to report it fast, then track it to closure without chasing anyone.
- **Technicians** working a personal queue all day: acknowledge, investigate, ask for info, resolve, under SLA pressure.
- **Administrators / IT managers** who watch service health, SLA compliance and workload, and configure services, SLAs, categories and assignment strategy.

## Product Purpose

I-ITSM manages the full incident lifecycle (Report → Classify → Prioritize → Assign → Investigate → Resolve → Verify → Close) with automation doing the triage: auto-categorisation, weighted priority, SLA deadlines, duplicate detection, smart assignment, escalation and notifications. Success = fewer SLA breaches, no lost incidents, and a complete audit trail.

## Positioning

Built for a campus IT organisation (BITS course project, candidate for adoption by Maintenance Division / CCIT). Must work as a real tool and also demo three workflows clearly in a 15–20 minute recorded video.

## Capabilities and Constraints

Three role-specific UIs with different navigation. Real-time updates via Server-Sent Events. All rules (allowed actions per role/state) come from the API's `allowedActions`; the UI never re-derives permissions.

## Brand Commitments

Product name: **I-ITSM** (confirmed by user).

## Evidence on Hand

Seeded demo organisation (12 services, 8 technicians, ~100 historical incidents). No real customer data, testimonials or metrics; do not fabricate any.

## Product Principles

1. Triage is automatic; humans confirm, not compute.
2. Every screen answers "what needs my attention now?" for its role.
3. Every change is explainable: priority reason, assignment reason, audit trail.
4. Demo-clarity and daily-use speed are both requirements.

## Accessibility & Inclusion

Keyboard-operable controls, visible focus, WCAG AA contrast, status never conveyed by colour alone.
