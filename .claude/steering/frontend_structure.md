# Frontend Project Structure

**Status: bare Vite scaffold.** Only the default `npm create vite@latest ... --template react-ts`
starter exists (`App.tsx` showing the Vite/React logos, no real code). Lines below marked
**(built)** exist today; everything else is the target layout to grow into as specs are
implemented. Unlike the backend, Claude has free rein here (see root `CLAUDE.md`'s Working
Agreement) — this doc exists for consistency, not to gate what Claude is allowed to write.

## Directory Layout (current + target)

```
frontend/
├── src/
│   ├── components/              # Not yet created — one subdirectory per feature area once
│   │                             #   components exist (mirrors the backend's feature grouping,
│   │                             #   e.g. Accounts/, Transactions/, Reminders/)
│   │
│   ├── pages/                   # Not yet created — add if/when routing needs page-level
│   │                             #   components; react-router-dom is installed but unused so far
│   │
│   ├── services/                # Not yet created — all backend API calls, one *Api.ts file per
│   │                             #   resource area, plus a shared client.ts request<T>() wrapper
│   │
│   ├── types/                   # Not yet created — centralized TypeScript types, one file per
│   │                             #   resource area
│   │
│   ├── hooks/                   # Not yet created — extract when a second component needs the
│   │                             #   same logic, not before
│   │
│   ├── App.tsx                  # (built) Default Vite starter — not yet the real orchestrator
│   ├── App.css                  # (built) Default Vite starter styles
│   ├── index.css                # (built) Default Vite starter styles
│   ├── main.tsx                 # (built) React entry point
│   └── test-setup.ts            # (built) Vitest + jest-dom setup
│
├── public/                      # (built) favicon.svg, icons.svg (Vite defaults)
│
├── vite.config.ts               # (built) React plugin, dev server on :5180 (strictPort),
│                                 #   /api → :8090 proxy
├── vitest.config.ts             # (built) jsdom env, globals, test-setup.ts
├── tsconfig.json / tsconfig.app.json / tsconfig.node.json   # (built)
├── .oxlintrc.json                # (built) oxlint config
├── package.json                 # (built)
├── package-lock.json             # (built)
└── .gitignore                    # (built)
```

**Installed but unused so far**: `react-router-dom`, `axios` — both added at scaffolding time to
match the reference projects' stack (see `tech.md`), neither has a real call site yet.

## File Naming Rules

| Category | Pattern | Example |
|---|---|---|
| React Components | `PascalCase.tsx` | `AccountList.tsx` |
| Component Tests | `PascalCase.test.tsx` | `AccountList.test.tsx` |
| Component Styles | `PascalCase.module.css` | `AccountList.module.css` |
| Services/Utils | `camelCase.ts` | `accountApi.ts` |
| Type Definitions | `camelCase.ts` | `account.ts` |
| Hooks | `use[Name].ts` | `useAccounts.ts` |

## Services & API Layer (target, once built)

All backend communication will go through `src/services/*Api.ts` — never raw `axios`/`fetch` in a
component. Each file exports a typed object following a shared `request<T>()` wrapper pattern
(`client.ts`): typed, throws a shared `ApiError` (`src/types/api.ts`) on failure.

## Type Definitions

Centralized in `src/types/` once it exists. Nullable backend fields are `T | null`, not
`T | undefined`.

## Environment Configuration

```
# frontend/.env.local (git-ignored, not present by default)
VITE_API_BASE=/api/v1
```

Only needed to override the Vite dev-server proxy. Not needed for normal `npm run dev` usage,
which proxies `/api` to `:8090` automatically.

## Testing Setup

- Run: `npm test` (single run) or `npm run test:watch`. Coverage: `npm run test:coverage`.
- Vitest + React Testing Library + jsdom, `test-setup.ts` wires up `@testing-library/jest-dom`.
- **jsdom doesn't render CSS** — a green Vitest suite is never sufficient sign-off for new/changed
  styling (contrast, light/dark theming, layout). Always do a real browser pass too.

## Build & Deployment

- Build: `npm run build` → `frontend/dist/`
- Deploy: not decided — local only for now (see `product.md`)

## Key Principles

1. **Type everything** — no `any` without justification
2. **Separation of concerns** — components for UI, `services/` for API, `types/` for contracts
3. **Test behaviour**, not implementation
4. **Keep it simple** — build to the current build-order step, not ahead of it
5. **Accessibility is not optional** — semantic HTML, `aria-label`s, `role="status"`/`role="alert"`
   where applicable (see `frontend_conventions.md`)
