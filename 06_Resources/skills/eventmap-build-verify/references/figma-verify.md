# Phase 3 — Verify screens against Figma

Goal: check that the implemented Compose screens match the design, and produce a concrete,
reviewable list of divergences — not a vague "looks fine". This only makes sense once the
app compiles (Phase 1); for data-driven screens, having the backend up (Phase 2) lets you
see real content instead of placeholders.

## The design source

- File key: **`Lf0iBDnxz7x80uA4LAAyDJ`**
- Name: **EventMap · Design v2 (Reels)**, dev mode, entry node `49-2`
- URL: `https://www.figma.com/design/Lf0iBDnxz7x80uA4LAAyDJ/EventMap-·-Design-v2--Reels-?node-id=49-2&m=dev`
- The API/data contract that the screens render against: `05_Docs/API_CONTRACT.md`

## Use the Figma MCP, not screenshots-by-eye

The Figma MCP server (tools `mcp__…__get_*`, `use_figma`) gives you the real design data —
exact spacing, colors, type, and component structure — which is what makes verification
objective. For each screen/node:

1. `get_metadata` — get the node tree and the child node IDs for the frame you're checking.
2. `get_screenshot` — render the frame so you can compare layout visually.
3. `get_variable_defs` — pull design tokens (colors, spacing, radii, type) actually used.
4. `get_design_context` (or `get_code`) — get the structured layout/spec for the node.

Then open the corresponding Compose screen in `02_Code/common/feature/<feature>/ui/` and
compare. Map Figma frames to features by name: Reels/feed → `feed/ui`, map → `map/ui`,
event card/detail → `event/ui`, auth → `auth/ui`, profile → `profile/ui`,
search → `search/ui`, video → `video/ui`.

If a Figma plugin/skill is installed (e.g. `/figma-use`), read it before calling
`use_figma`, per the Figma MCP instructions.

## What to compare, per screen

Go attribute by attribute so the verdict is concrete and actionable:

- **Layout & spacing** — paddings, gaps, alignment vs the frame's auto-layout values.
- **Color** — backgrounds, text, accents vs `get_variable_defs` tokens (check the actual
  hex, don't trust names).
- **Typography** — font, size, weight, line height.
- **Components present** — every element in the design exists in code (and nothing extra).
- **States** — empty / loading / error / selected where the design specifies them.
- **Data binding** — fields shown match the API contract (`05_Docs/API_CONTRACT.md`).

## Output format

Produce one verdict block per screen so the user can act on it:

```
### <Screen name> — PASS | DIFFERS
Figma node: <id>     Code: 02_Code/common/feature/<f>/ui/<File>.kt
- <attribute>: design = <value>, code = <value>  → <fix or OK>
```

List concrete diffs with the exact code file to change. Summarize at the end: how many
screens pass, how many differ, and the highest-impact fixes. Don't claim "100% matches"
unless every in-scope screen passed attribute-by-attribute — partial honesty is more
useful than false completeness.

## Scope

If the user hasn't named specific screens, confirm scope first (all screens vs the few
they care about). Verifying every frame is expensive; usually a handful of key screens
(map, feed/reels, event detail, auth) is what's meant by "сверь проект с фигмой".
