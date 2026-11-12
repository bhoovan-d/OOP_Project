---
version: 1
slug: "frontend-src-app-tsx"
primary_target: "frontend/src/App.tsx"
related_targets: []
---

# Surface: I-ITSM web app (all role shells)

Mode: Operate. Audience: employees reporting, technicians working a queue, admins watching service health. Code-led.

## Direction contract

THESIS: The app is a triage board: every incident wears a priority tag and its SLA is a visible, shrinking duration. Refuses the grey SaaS table with a coloured pill.

OWN-WORLD: White enamel ground, deep ward-navy sidebar, cool grey hairlines. Triage tag colours carry priority only: red P1, orange P2, amber P3, green P4. Workhorse sans (Inter Tight-free: "Manrope" body/UI) with tabular mono digits (JetBrains Mono) for times and counters.

STORY: Visitor sees what needs attention now, ordered by urgency, with time left made physical; acts in one click via role-appropriate actions.

FIRST VIEWPORT: Left navy rail (role nav), top bar (search, live dot, bell, user). Main: KPI strip, then the triage queue table; each row starts with a tag + SLA strip. Primary action (Report incident / next action) top right.

FORM: Triage board (ER whiteboard), candidate 3 of 7. seed:5e9fce187c734990. Raises: live mono countdown (from specimen), strip length = exact duration (from labanotation), capacity bars that must not overflow (from j-card), incident number as headline identity (from catalog sleeve), destructive actions isolated (from graphite console).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
