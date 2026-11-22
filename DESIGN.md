---
name: I-ITSM
description: Campus IT incident management, set out like a ground scoreboard.
colors:
  board: "#0f3b2e"
  board-2: "#14553f"
  board-line: "#23664f"
  chalk: "#c4dccf"
  plate: "#f7f7f2"
  ground: "#e9eee8"
  surface: "#ffffff"
  sunken: "#f1f4f0"
  line: "#d8dfd8"
  line-strong: "#b4c0b7"
  ink: "#111814"
  ink-2: "#33403a"
  ink-3: "#56635c"
  action: "#14553f"
  action-hover: "#0f3b2e"
  action-soft: "#e2efe7"
  p1: "#c62a1e"
  p2: "#d9650c"
  p3: "#c79100"
  p4: "#2f6db5"
  ok: "#1d7a49"
  warn: "#9a6500"
  bad: "#c0261b"
typography:
  plate:
    fontFamily: "Barlow Condensed, Barlow, sans-serif"
    fontSize: "52px"
    fontWeight: 800
    lineHeight: 0.9
    fontFeature: "tnum"
  display:
    fontFamily: "Barlow Condensed, Barlow, sans-serif"
    fontSize: "30px"
    fontWeight: 700
    lineHeight: 1.05
  title:
    fontFamily: "Barlow Condensed, Barlow, sans-serif"
    fontSize: "18px"
    fontWeight: 700
    lineHeight: 1
  body:
    fontFamily: "Barlow, sans-serif"
    fontSize: "14.5px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Barlow, sans-serif"
    fontSize: "11.5px"
    fontWeight: 600
    letterSpacing: "0.12em"
rounded:
  plate: "2px"
  chip: "3px"
  control: "4px"
  panel: "6px"
spacing:
  gutter-mobile: "16px"
  gutter-desktop: "32px"
  gap: "20px"
components:
  button-primary:
    backgroundColor: "{colors.action}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    height: "36px"
    padding: "0 14px"
  button-primary-hover:
    backgroundColor: "{colors.action-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    height: "36px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    height: "36px"
  plate:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    typography: "{typography.plate}"
    rounded: "{rounded.plate}"
  plate-reversed:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.surface}"
    rounded: "{rounded.plate}"
  nav-item-active:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.chip}"
    height: "40px"
---

# Design System: I-ITSM

## Overview

**Creative North Star: "The Scoreboard"**

I-ITSM is laid out like a hand-operated cricket ground scoreboard. The shell is painted board green; every live count, countdown and incident number hangs on it as a white tin plate with two pin holes, set in heavy condensed numerals that read from the back of a lecture hall. The working surfaces (tables, forms, conversation) sit on a pale grass-grey ground in plain white panels, so the board carries the drama and the work stays calm.

Every role opens on its own board: admins on the operations board, technicians on their queue board, employees on "my board". Each board has exactly one reversed, dark plate that names the single thing needing attention now (a major incident, the incident most likely to breach, the next one to work, the reply IT is waiting for): its priority tag, incident number and time left in plate numerals, with a coloured lead word opening the detail line beneath. Everything else is ranked by scale: big plate numerals, small tracked labels, no decorative cards.

Motion is that of a plate turned by hand: values flip in a short stepped frame, and nothing in the app eases or fades. The look replaces the earlier navy "Triage Board" world; only the P1–P4 priority tags were carried over, redrawn as painted plates.

**Key Characteristics:**
- Board green shell and boards; white hung plates for every live number
- One type family: Barlow for reading, Barlow Condensed for numbers, headings and plates
- One reversed plate per screen for what needs attention now
- SLA time drawn as twelve ball slots that fill as the window is used
- Stepped, two-frame state changes; no easing anywhere

## Colors

A restrained grass-and-chalk neutral set under one committed board green, with four priority colours held strictly in reserve.

### Primary
- **Board Green** (board, board-2): the sidebar, every board strip, the SLA clock, the live triage panel, and the primary button. Board-2 is the lighter lane used for hover, active rows, filled ball slots and "in progress" status.
- **Chalk** (chalk): all text and labels painted directly on the board.
- **Plate White** (plate): the hung number plates and the active navigation item.

### Secondary
- **Priority Red / Orange / Amber / Blue** (p1–p4): P1–P4 tags only, plus SLA danger (red breached, amber at risk). P3 amber carries dark ink text.
- **Charts** stay inside the world: a single series is solid board green; the opened-vs-resolved pair is solid board green against an outlined plate, told apart by fill rather than hue.

### Neutral
- **Grass Ground** (ground): the page background behind panels.
- **Surface / Sunken** (surface, sunken): panels; table heads, tracks and hover rows.
- **Hairlines** (line, line-strong): 1px panel borders and dividers; input and empty-slot strokes.
- **Ink ladder** (ink, ink-2, ink-3): text at three levels, all at least 4.5:1 on white.

### Named Rules
**The Reserved Paint Rule.** Priority colours appear only on P-tags and SLA danger states. "Unassigned", warnings and icons use `warn` amber-brown, never P2 orange.

**The One Reversed Plate Rule.** Each screen hangs exactly one dark plate, and it always names the most urgent single item for that role.

## Typography

**Display Font:** Barlow Condensed (with Barlow, sans-serif)
**Body Font:** Barlow (with system sans)

**Character:** One superfamily in two cuts: the condensed cut is the sign-painter's numerals on the board, the regular cut is the reading voice for tables and forms.

### Hierarchy
- **Plate** (800, 52px, 0.9, tabular): board counts. Reversed "now" plates set the title at 30–34px.
- **Display** (700, 30px, 1.05): page titles; incident numbers at 34px and incident titles at 28–32px.
- **Title** (700, 18px condensed): panel titles and dialog titles.
- **Body** (400–600, 14.5px, 1.5): tables, forms, descriptions; long text held to 75ch.
- **Label** (600, 11.5px, 0.12em uppercase): plate captions, table heads (0.1em), and section labels in the sidebar.

### Named Rules
**The One Family Rule.** No third face. Numbers, incident IDs, countdowns and scores use Barlow Condensed with tabular figures; there is no monospace.

## Layout

A 232px board-green sidebar (a drawer below 1024px) and a 60px white top bar frame a content column of at most 1480px, with 16px gutters on phones, 24px on tablets and 32px on desktop. Pages open with the page title, then a board strip whose first cell is the reversed plate (wider, about 1.7 columns) followed by four to six count plates; boards fold to two columns on phones with the reversed plate full width. Panels follow in two-column grids that stack below 1280px. Wide tables scroll horizontally inside their panel rather than squeezing columns.

## Elevation & Depth

Flat by default; depth only where something hangs. Plates carry a short drop (`0 1px 0 rgba(0,0,0,.25), 0 3px 6px -3px rgba(0,0,0,.35)`) so they read as tin on the board. Popovers and dialogs lift with a soft green-black shadow (`0 24px 60px -12px rgba(8,30,22,.45)`) over a 50% board backdrop. Panels have no shadow, only a 1px hairline.

## Shapes

Square-cut and painted: plates and tags at 2px, chips at 3px, buttons and inputs at 4px, panels and boards at 6px. Plates carry two 5px pin holes at the top corners. Status and health markers are squared, never pills; the only full circles are SLA ball slots, the live dot, radio buttons and switches.

## Components

### Buttons
- **Shape:** squared (4px), 36px tall, 32px small.
- **Primary:** board green with white text; hover darkens to the board's deepest green.
- **Secondary / Ghost / Danger:** white with a strong hairline; text-only with a sunken hover; white with a red hairline and red text, kept apart at the right of an action bar.

### Chips
- **Style:** 24px squared chips tinted by lifecycle state: neutral for new, green-soft for assigned, solid board green for in progress, violet for waiting on the reporter, green for resolved, struck through for cancelled.
- **Selected (category picks):** a white plate on the board; unselected are chalk outlines.

### Cards / Containers
- **Corner Style:** 6px.
- **Background:** white on the grass ground; boards are board green.
- **Border:** 1px hairline; panel heads are 48px with a condensed title.

### Inputs / Fields
- **Style:** white, 1px strong hairline, 4px radius, 36px.
- **Focus:** board-green border with a 3px 15% green ring. Errors switch to red border and ring.

### Navigation
- **Style:** chalk text on board green, 40px rows. The active item hangs as a white plate with ink text; the first item carries a live count on a small plate.

### Plate and Board (signature)
The board is a 6px board-green strip. Each count is a plate (value) over a chalk caption, linking to the filtered list it counts. The reversed plate is the same shape in ink: priority tag, incident number and time left on one line, then a detail line opened by a coloured lead word (red danger, amber risk, green all clear, violet waiting on the reporter). No caption sits above the number.

### SLA ball slots (signature)
Twelve 8px circles in two groups of six; filled slots are time used. Tones: board green on course, amber at risk, red breached, dashed grey paused. A condensed countdown follows, reading "late" once overdue, never with a minus sign. The incident page draws the same SLA on the board as two rulers, with the resolution countdown on the reversed plate.

## Do's and Don'ts

### Do:
- **Do** hang every live number as a plate on a board, with a chalk caption under it.
- **Do** give each screen one reversed plate for the most urgent single item, and only one.
- **Do** set numbers, incident IDs and countdowns in Barlow Condensed with tabular figures.
- **Do** change state in stepped frames (`steps()` / `step-end`); flip plates on new values.

### Don't:
- **Don't** use P1–P4 colours for anything but priority tags and SLA danger.
- **Don't** add eased transitions, fades or animated chart entrances.
- **Don't** reintroduce rounded pills, a monospace face, or the old navy rail.
- **Don't** wrap content in same-size icon cards; hierarchy comes from scale, not boxes.
- **Don't** tag healthy things: only outages and degraded services get a filled marker; healthy reads as plain grey text.
- **Don't** put a caps caption above a plate's headline; lead words go in the detail line below it.
