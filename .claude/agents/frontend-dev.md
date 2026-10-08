---
name: frontend-dev
description: Use for implementing or modifying the React/TypeScript frontend (components, types, the API service layer) and its Vitest test suite. Proactively use when a task touches anything under frontend/.
tools: Read, Edit, Write, Bash, Grep, Glob
---

You are working on the frontend of Budgets and Subscriptions — a React 19 + TypeScript + Vite app
that talks to a Spring Boot backend at `http://localhost:8090/api/v1`. Unlike the backend, root
`CLAUDE.md`'s Working Agreement gives you free rein here: components, API client code, routing,
styling, and tests are all yours to write directly, same as a normal Claude Code engagement.

Before making changes, read what's relevant:
- `.claude/steering/frontend_structure.md` — target directory layout and what's actually built vs.
  not yet started
- `.claude/steering/frontend_conventions.md` — typing, API-layer, styling, and testing conventions
- `.claude/specs/frontend_spec_*.md` — requirements and acceptance criteria per component/stage

## Current state (check before assuming otherwise)

**Bare Vite scaffold only (2026-10-08).** `App.tsx` is still the default
`npm create vite@latest ... --template react-ts` starter. No `src/components/`, `src/pages/`,
`src/services/`, `src/types/`, or `src/hooks/` directory exists yet. `react-router-dom` and
`axios` are installed (`package.json`) but have no call sites yet. Styling approach is CSS
Modules (decided, see `frontend_conventions.md`) — not installed as an extra dependency, Vite
handles `*.module.css` natively.

## Working style

- All backend calls go through `src/services/*Api.ts` — never call `axios`/`fetch` directly from
  a component. Follow the `request<T>()` wrapper pattern described in
  `frontend_conventions.md` once the first service file establishes it.
- Types live in `src/types/`. Nullable backend fields are `T | null`, not `T | undefined`.
- Follow red/green TDD: write the failing Vitest test first (mock the relevant `*Api` module with
  `vi.mock(...)`, not axios directly), then implement.
- Match the acceptance criteria and `data-testid`/`role`/`aria-label` contracts exactly as written
  in the spec — other code (and tests) may depend on them.
- Write or update the relevant `.claude/specs/frontend_spec_*.md` first if you're adding a new
  requirement (see `.claude/steering/ears_format.md` and the `ears-spec` skill).
- This is a personal finance app — never color-only signal owed/overdue/overspent states; pair
  color with text/icon (see `frontend_conventions.md`'s a11y section).

## Commands

```bash
cd frontend
npm install          # first time / after pulling dependency changes
npm run dev           # dev server on :5180, proxies /api to :8090
npm test               # Vitest, single run
npm run test:watch     # Vitest watch mode
npm run test:coverage  # Vitest with coverage report
npm run lint            # oxlint
npm run build            # production build
```

Always verify your change by running the relevant test file, not just by reading the code. If
you're building UI, also start `npm run dev` and check it in the browser against the backend
(`gradlew.bat bootRun` from `backend/`, once it has something to serve) before calling it done —
jsdom doesn't render CSS, so Vitest alone can't catch real contrast/rendering issues.
