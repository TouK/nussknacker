# AGENTS.md — Nussknacker Designer Client

React 18 / TypeScript 5.9 / Webpack frontend for the Nussknacker streaming data processing designer.

## Build & Development

```bash
npm run build              # Production build (outputs to dist/)
npm run start              # Dev server at localhost:3000 (needs backend on :8080)
npm run check-types        # TypeScript type checking (tsc --noEmit)
npm run lint               # ESLint --fix + syncpack lint
npm run check              # check-types + lint with i18n string checks
```

## Testing

### Unit Tests (Jest + React Testing Library)

```bash
npm run test:unit                              # Run all unit tests (includes test:types)
npx jest --testPathPattern="NodeUtils"         # Run a single test file by name
npx jest test/reducer-test.ts                  # Run a specific test file by path
npx jest --testNamePattern="should merge"      # Run tests matching a description
npx jest --watch                               # Watch mode
```

Test files use both `*.test.ts(x)` and `*-test.ts(x)` naming. They live either co-located
next to source files in `src/` or in the top-level `test/` directory.

### Type Tests (tstyche)

```bash
npm run test:types         # Run type-level tests
```

### E2E Tests (Cypress)

**Cypress tests require a running frontend + backend.** Do NOT start the backend yourself — it is
resource-heavy. Check if the environment is already running first:

```bash
curl -sf http://localhost:8080/api > /dev/null && curl -sf http://localhost:3000 > /dev/null && echo "ready"
```

If both are up:

```bash
npm run test:e2e           # Run Cypress headless
npm run test:e2e:dev       # Open Cypress interactive runner
```

## Code Style

### Formatting (Prettier — `.prettierrc.json`)

-   **4 spaces** indentation, **140 char** print width
-   **Semicolons** required, **trailing commas** everywhere
-   Double quotes, LF line endings, final newline required

Run `npx prettier --write <file>` to format. Enforced via lint-staged on commit.

### Imports (ESLint `import/order` — error)

Two groups separated by a blank line, alphabetically sorted (case-insensitive) within each:

```typescript
// 1. External packages (builtin + external)
import { css, cx } from "@emotion/css";
import type { WindowContentProps } from "@touk/window-manager";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

// 2. Internal (parent/sibling/index)
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import type { WindowKind } from "../../windowManager/WindowKind";
```

Critical import rules:

-   **Type-only imports must use `import type { X }`** — `@typescript-eslint/consistent-type-imports` is `error`
-   **Wildcard imports (`import * as X`) are banned** — use named imports
-   Do NOT import from internal paths of libraries (e.g. `pkg/src/internal/...`) — use public API

### TypeScript Conventions

-   **`type` preferred** for data shapes and unions; `interface` for component props and object contracts
-   **String enums** and **`const enum`** for constant groupings
-   **`$TodoType`** is the escape-hatch type for untyped legacy code — avoid introducing new usages
-   `noEmit: true` — TypeScript is checking only; Babel transpiles
-   `skipLibCheck: true` — but importing from `/src/` of node_modules bypasses this

### Naming Conventions

| Element           | Convention                             | Example                               |
| ----------------- | -------------------------------------- | ------------------------------------- |
| React components  | PascalCase                             | `GenericConfirmDialog.tsx`            |
| Hooks             | camelCase with `use` prefix            | `useWindows.ts`, `useUserSettings.ts` |
| Utilities/helpers | camelCase                              | `utils.ts`, `api.ts`                  |
| Styled components | PascalCase, often in separate files    | `Styled.tsx`, `StyledComment.tsx`     |
| Test files        | `{name}.test.tsx` or `{name}-test.tsx` | `NodeUtils-test.js`                   |
| Directories       | camelCase (some PascalCase legacy)     | `windowManager/`, `toolbars/`         |

### Internationalization (i18n)

**All user-facing literal strings must use `react-i18next`** — `i18next/no-literal-string` is `error`.
Use `t()` from `useTranslation()` for all UI text.
Disable with `/* eslint-disable i18next/no-literal-string */` only in non-UI files (configs, tests).

### Styling

Three approaches coexist — match the surrounding code:

1. **`@emotion/css`** — ad-hoc class generation: `css({ display: "flex", gap: 8 })`
2. **MUI `styled()`** — reusable themed components: `styled("div")(({ theme }) => ({ ... }))`
3. **MUI `sx` prop** — inline one-off theming on MUI components

### State Management (Redux)

-   Store via `@reduxjs/toolkit` `configureStore`; reducers use **switch/case** (not `createSlice`)
-   Immutable updates via **`immer` `produce()`** directly
-   Typed hooks: `useAppDispatch()` and `useAppSelector()`
-   `redux-undo` for undo/redo, `redux-persist` for persistence

### React Patterns

-   Functional components only (no new class components)
-   `useCallback`/`useMemo` for memoization; `react-hooks/exhaustive-deps` is `error`
-   Additional exhaustive-deps hooks: `useDrop`, `useDrag`, `useCallbackRef`, `useRegisterCommands`
-   `forwardRef` components must set `displayName` manually
-   Error boundaries via `react-error-boundary`; `prop-types` disabled — use TypeScript

### Error Handling

-   Try/catch at the point of use in hooks and async operations
-   `ErrorBoundary` wraps the app root for React rendering errors
-   `console.warn` for non-critical issues; no global error utility

### Testing Patterns

```typescript
import { render, screen, waitFor } from "@testing-library/react";

describe("ComponentName", () => {
    it("should do specific thing", () => {
        render(<Component />);
        expect(screen.getByText("expected")).toBeInTheDocument();
    });
});
```

-   Wrap components in providers: `<Provider store={store}>`, `<NuThemeProvider>`
-   Snapshot serializer: `@emotion/jest/serializer`; `clearMocks: true` globally
-   `jest.mock()` for module mocking; parameterized tests via `it.each`

## Architecture Notes

-   **Module federation** via `@touk/federated-component` for runtime plugin loading
-   `designer/submodules/` is a separate workspace loaded as a remote module at runtime
-   `types/@remote/` contains shared type declarations for federated modules
-   `patches/` dir with `patch-package` fixes applied via `postinstall`

## Environment

-   **Node.js**: 20.x LTS (`lts/iron` in `.nvmrc`) — minimum 20.10.0
-   **npm**: minimum 10.2.3
-   Pre-commit hooks via **husky** + **lint-staged** (eslint --fix + prettier --write)
