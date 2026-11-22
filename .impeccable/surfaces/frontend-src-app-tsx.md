---
version: 1
slug: "frontend-src-app-tsx"
primary_target: "frontend/src/App.tsx"
related_targets: []
---

# Surface: I-ITSM web app (all role shells)

Mode: Operate. Audience: employees reporting, technicians working a queue, admins watching service health. Code-led. Redesign (Nov 2026): replaces the former "Triage Board" world; only the P1–P4 priority tags survive, redrawn.

Seen on projectors in lit rooms (demo, grading) and through long technician shifts: light ground, dark green shell.

## Direction contract

THESIS: The app is a ground scoreboard: every live count, countdown and incident number hangs as a white plate on painted board green, legible from the back row. Refuses the grey SaaS card grid with KPI tiles and coloured pills.

OWN-WORLD: Board green rail and scoreboard strips (#0f3b2e / #14553f), white tin plates (#f7f7f2) with pin holes, pale grass-grey ground (#e9eee8), near-black ink. Priority red/orange/amber/blue only on P-tags and SLA danger. One family: Barlow for UI, Barlow Condensed 700–800 for plate numerals, headings, incident numbers (tabular). SLA drawn as a row of ball-slots that fill as time is used. Raises: one family only (annual); stepped two-frame plate flips, no easing (acetate); exactly one reversed dark plate per screen for the single most urgent thing (timetable); scale contrast, not boxes, carries hierarchy (specimen).

STORY: Each role opens on its board: what needs attention now sits on the reversed plate, the counts hang beside it, the queue reads by time left, and one click acts.

FIRST VIEWPORT: 232px green rail (brand plate, role nav with count plates), 60px white top bar (search/jump-to-INC, live dot, bell, primary action right). Main: page title in condensed caps, then the green board strip (reversed "now" plate + 5 hung count plates), then queue table (tag, INC plate number, title/service, assignee, status, ball-slot SLA + time left) beside a service board.

FORM: Cricket ground scoreboard, candidate 6 of 7. seed:c794cbe8ab5cf725.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
