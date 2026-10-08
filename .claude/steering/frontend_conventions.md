# Frontend Conventions

## File Structure & Naming

- **Components**: `PascalCase.tsx`
- **Utilities/Services**: `camelCase.ts`
- **Types**: `camelCase.ts` files exporting `PascalCase` interfaces/types
- **Tests**: `ComponentName.test.tsx` or `fileName.test.ts`, colocated with source

## TypeScript Type Conventions

- Types live under `src/types/`, one file per resource area
- Use `interface` for object shapes, `type`/`enum` for unions or fixed value sets
- All API responses are typed; no `any` without justification
- Optional fields that can be `null` from the backend are typed `T | null`, not `T | undefined`

## API Communication

- All backend calls go through `src/services/*Api.ts` — never call `axios`/`fetch` directly from
  a component
- Base URL comes from `import.meta.env.VITE_API_BASE`, falling back to
  `http://localhost:8090/api/v1` (Vite env convention — not `process.env`)
- Errors are centralized: a shared `request<T>()` wrapper catches axios errors and throws a typed
  `ApiError` (`src/types/api.ts`) with `status`, `message`, and optional `details`

Example shape to follow once the first service file is built:
```typescript
import axios from 'axios';
import type { Account } from '../types/account';
import { ApiError } from '../types/api';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8090/api/v1';
const client = axios.create({ baseURL: API_BASE });

export const accountApi = {
  getAll: (): Promise<Account[]> =>
    request<{ data: Account[]; count: number }>(() => client.get('/accounts')).then(r => r.data),
  // ...
};
```

## Component Patterns

### Presentational Components
- Receive data via props
- Handle only UI logic (show/hide, clicks, forms)
- No direct API calls

### Container Components
- Manage state (`useState`, `useReducer`, or Context)
- Fetch data via `useEffect`
- Coordinate between presentational components

### Hooks (Custom)
- Extract reusable logic into hooks, prefixed `use`
- One responsibility per hook
- Extract when a second component needs the same logic, not before

## State Management

- React hooks (`useState`, `useContext`) for local state
- `useEffect` for side effects (data fetching)
- Context API for global state if needed later — not Redux
- Avoid prop drilling; lift state up or use Context

## Routing

`react-router-dom` is installed but unused so far. Add routes once there's a second real page to
navigate to (e.g. login vs. the main app) — don't scaffold a router ahead of a concrete need.

## Styling

**CSS Modules** — one `ComponentName.module.css` colocated per component, imported as
`import styles from './ComponentName.module.css'` and applied via `className={styles.foo}`. No
extra dependency; Vite scopes `*.module.css` automatically. Global resets/base styles (currently
the default Vite `index.css`/`App.css`) stay for app-wide concerns only — component-specific
styling goes in its own module. Not Tailwind.

Once theming (light/dark) is needed, follow the reference projects' pattern: a global,
non-module stylesheet owning `:root` theme custom properties (`--text`, `--bg`, `--border`,
`--accent`, ...), imported once from `main.tsx`, so every component's module references them via
`var(--text)` rather than hardcoded hex values — this is what keeps contrast correct across
light/dark without per-component rework.

## Testing Strategy (Vitest + React Testing Library)

- Test user interactions, not implementation details
- Use `render()` and `screen` to query elements
- Mock the service layer with `vi.mock('../services/xApi')`, not axios directly, in component
  tests
- One test file per component, colocated

Example:
```typescript
import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { AccountList } from './AccountList';
import { accountApi } from '../services/accountApi';

vi.mock('../services/accountApi');
const mockGetAll = vi.mocked(accountApi.getAll);

describe('AccountList', () => {
  it('should render a list of accounts', async () => {
    mockGetAll.mockResolvedValue([{ id: '1', name: 'Current Account' } as any]);
    render(<AccountList />);
    expect(await screen.findByText('Current Account')).toBeInTheDocument();
  });
});
```

## Environment Variables

- Create `.env.local` in `frontend/` (git-ignored)
- Use `VITE_` prefix
- Example: `VITE_API_BASE=http://localhost:8090/api/v1`
- Access via `import.meta.env.VITE_API_BASE`

## Code Style

- Use arrow functions for callbacks
- Destructure props and imports
- Keep components under ~200 lines (split if larger)
- Use meaningful variable names; avoid abbreviations
- No comments unless the WHY is non-obvious

## Error Handling

- Display user-friendly error messages, driven by `ApiError.message`
- Never expose raw backend stack traces or `ApiError.details` internals directly to the user
- Log errors to console only in dev (`import.meta.env.DEV`)

## Accessibility (a11y)

- Use semantic HTML: `<button>`, `<label>`, `<form>`, etc.
- Include `aria-label` on icon-only buttons
- Ensure form inputs have associated `<label>` elements
- Loading indicators need `role="status"`; error containers need `role="alert"`
- This is a personal finance app handling sensitive numbers — never color-only signal for
  owed/overdue/overspent states (red text alone, etc.); pair color with text/icon so it reads
  correctly for colorblind users and under a contrast check

## Performance

- Lazy load routes (`React.lazy` + `Suspense`) once routing exists
- Memoize components if props don't change (`React.memo`)
- Not a real concern at this app's scale (single/small-user) — don't over-optimize prematurely
