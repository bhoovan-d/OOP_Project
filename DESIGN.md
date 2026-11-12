# I-ITSM design system: "Triage Board"

The app is an ER triage board for IT. Every incident wears a **priority tag** and its SLA is a visible, shrinking **duration strip**. Operate-mode product UI: standard navigation, tables and forms; the world contributes palette, type, density and that one signature.

Source of truth for tokens: `frontend/src/index.css` (`@theme`). Mirror: `.impeccable/design.json`.

## Colour (Restrained + reserved triage colours)

| Token | Value | Job |
|---|---|---|
| `ground` | `#f4f6f9` | page background (enamel white, cool) |
| `surface` / `sunken` | `#ffffff` / `#eef1f5` | panels / table heads, tracks, hover |
| `line` / `line-strong` | `#e1e6ee` / `#c9d1dd` | 1px hairlines / input borders |
| `ink` / `ink-2` / `ink-3` | `#0e1726` / `#3c4a60` / `#5b687c` | text levels (all ≥ 4.5:1 on white) |
| `rail` / `rail-2` / `rail-ink` | `#0f2341` / `#1a3358` / `#c7d2e4` | ward-navy sidebar, active item, sidebar text |
| `action` | `#1f4fd1` | primary buttons, selection, focus ring, links |
| `p1` `p2` `p3` `p4` | `#d42a1e` `#e2620f` `#c98a04` `#2b8a3e` | **priority only** (tags, SLA danger/risk). P3 tag uses dark ink text |
| `ok` `warn` `bad` | `#2b8a3e` `#b7790a` `#c8261b` | state text |

Rules: priority colours never decorate; they always ship with the `P1–P4` text. Charts use a separate validated pair (`#1f4fd1` opened, `#0e9384` resolved). Single-series charts are navy `#1a3358`.

## Type

- UI: **Manrope Variable** (self-hosted via @fontsource). Body 14px/1.5; page titles 22–24px extra-bold; panel titles 13.5px bold; table heads 11.5px uppercase +0.06em.
- Data: **JetBrains Mono** 400/600 with tabular figures, only for incident numbers, countdowns, counts, scores and timestamps.

## Signature components

- **TriageTag**: luggage-tag shape (left radius 9px, eyelet dot), `P1…P4`, sizes sm/md/lg; optional label ("P1 Critical").
- **SlaStrip**: 6px track, fill = remaining fraction of the window, live `mm:ss` / `3h 20m` mono countdown from a shared 1s clock. Tones: ok ink-2, at risk p3 (< 25% left or server AT_RISK), breached p1, paused grey, met green.
- **SLA clock** (incident page): response and resolution drawn on one ruler with deadline ticks and overdue overrun.
- **CapacityBar**: one segment per workload slot; amber at ≥ 80%, red at limit; never overflows.

## Layout & components

- Shell: 248px navy rail (role-specific nav) + 64px top bar (search/jump-to-INC, live dot, notifications, primary action). Collapses to a drawer below 1024px.
- Panels: white, 1px `line`, 8px radius, 48px header with hairline. No nested cards, no coloured side borders.
- Buttons 36px (32px small), 6px radius: primary / secondary / ghost / danger. Destructive actions sit apart at the right of the action bar.
- Inputs 36px, 1px `line-strong`, focus = `action` border + 3px 15% ring.
- Status chips: pill, tinted per lifecycle state; CANCELLED struck through.
- Dialogs: native `<dialog>` (focus trap, Esc), navy 40% backdrop.

## Motion

150–250ms colour transitions; SLA strips animate width linearly each second; new/changed rows flash `action-soft` once (1.6s ease-out) on live events; live dot pulses. All disabled under `prefers-reduced-motion`.
