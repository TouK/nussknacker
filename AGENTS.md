# AGENTS.md — Nussknacker

Multi-module Scala 2.13 / TypeScript 5.9 project. Backend uses SBT, frontend uses npm workspaces.
Two frontend sub-projects live under `designer/client/` and `designer/submodules/` — each has its own
`AGENTS.md` with frontend-specific details. This file covers the **root project and Scala backend**.

## Build & Test (Scala / SBT)

```bash
sbt compile                                             # Compile all modules
sbt test                                                # Run all unit tests (excludes @Slow and @Network)
sbt "moduleName/test"                                   # All tests in one module
sbt "moduleName/testOnly *ClassName"                    # Single test class
sbt "moduleName/testOnly *ClassName -- -t \"test name\""  # Single test case by name
sbt "moduleName/slow:test"                              # Run slow tests (@Slow tag)
sbt "moduleName/ExternalDepsTests/test"                 # Run external-deps tests (@Network tag)
sbt "moduleName/IntegrationTest/test"                   # Integration tests (e.g. Flink)
sbt scalafmtCheckAll                                    # Check formatting
sbt scalafmtAll                                         # Auto-format all Scala sources
sbt scalafix                                            # Run scalafix rules
```

Module name examples: `designer`, `scenarioCompiler`, `commonApi`, `flinkDeploymentManager`,
`liteK8sDeploymentManager`, `schemedKafkaComponentsUtils`.

## Build & Test (Frontend)

Run from `designer/client/` or `designer/submodules/`:

```bash
npm run build                  # Production build
npm run start                  # Dev server (client :3000, submodules :5001)
npm run lint                   # syncpack-lint + eslint --fix
npm run test:unit              # Jest tests (client only)
npx jest --testPathPattern="ComponentName"       # Single test by file pattern
npx jest --testNamePattern="should render"       # Single test by description
npx jest path/to/file.test.tsx                   # Single test by path
npm run test:e2e               # Cypress E2E (client only)
npm run check-types            # TypeScript type checking (tsc, client only)
```

## Scala Code Style

### Formatting (scalafmt 3.9.1)

- **Max 120 characters** per line
- Dialect: `scala213source3`
- Alignment preset: `more`
- Trailing commas: `keep` (preserve existing)
- Dangling parentheses on definition and call sites
- Docstrings: `keep` (preserve HTML-style scaladoc)

### Import Ordering (scalafmt `scalastyle` sort)

Three groups separated by blank lines:

```scala
// 1. External (non-java, non-scala)
import io.circe._
import pl.touk.nussknacker.engine.api._

// 2. Java and Scala standard library
import java.time.Instant
import scala.collection.immutable.List

// 3. Uppercase aliases (type aliases)
import MyType
```

### Compiler Settings

- Scala **2.13.18**, Java target **11** (`--release 11`)
- `-Xfatal-warnings` — all warnings are errors
- `-Wconf:cat=deprecation:silent` — deprecation warnings silenced
- Kind-projector compiler plugin enabled
- SemanticDB enabled (for scalafix)

### Scalafix

Custom rule: `NoSlickTableOrPlainSqlWithoutSchema` — enforces schema in Slick queries.

### Naming Conventions

| Element        | Convention         | Example                            |
| -------------- | ------------------ | ---------------------------------- |
| Packages       | reverse-domain     | `pl.touk.nussknacker.engine.api`   |
| Classes/Traits | PascalCase         | `LoggedUser`, `SourceFactory`      |
| Objects        | PascalCase         | `CirceUtil`, `ProcessService`      |
| Methods        | camelCase          | `decodeJsonUnsafe`, `findById`     |
| Case classes   | `final case class` | `final case class CommonUser(...)` |
| Test classes   | `*Spec` or `*Test` | `DBProcessServiceSpec`             |

### Error Handling

- **Sealed trait hierarchies** for domain errors
- **`Either[Error, Result]`** for operation results
- **Cats `Validated` / `ValidatedNel`** for accumulating validation
- Custom exception classes extending standard ones
- Logging via `LazyLogging` (`com.typesafe.scalalogging`)
- No global error handler — handle errors locally

### Testing (ScalaTest 3.2 + ScalaCheck)

- Preferred styles: `AnyFunSuite`, `AnyFlatSpec`, `AnyWordSpec`
- Assertions: `shouldBe`, `should equal(...)`, `should be thrownBy`
- Common mixins: `Matchers`, `OptionValues`, `EitherValuesDetailedMessage`,
  `TableDrivenPropertyChecks`, `PatientScalaFutures`, `BeforeAndAfterEach`
- `@Slow` tag for slow tests, `@Network` tag for tests needing external services

### Patterns

- FP style: cats, Either, Validated, Future
- Companion objects with factory methods
- AnyVal extension methods (`implicit class RichX(val x: X) extends AnyVal`)
- Circe for JSON serialization
- Pekko (formerly Akka) for actor-based components

## TypeScript / Frontend Code Style

### Formatting (Prettier)

- **4 spaces** indentation, **140 char** line width
- Semicolons required, trailing commas everywhere (`"all"`)
- Double quotes (Prettier default)
- LF line endings

### Import Ordering (ESLint `import/order`)

Three groups separated by blank lines, alphabetically sorted (case-insensitive):

```typescript
// 1. Builtin (node)
import path from "path";

// 2. External packages
import type { Theme } from "@mui/material";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

// 3. Internal (parent, sibling, index)
import { SomeComponent } from "../components/SomeComponent";
import type { Settings } from "./types";
```

- **Type-only imports**: `import type { X }` required (`@typescript-eslint/consistent-type-imports`)
- **Wildcard imports banned**: `import * as X` → use named imports
- Level: **error** in `client`, **warn** in `submodules`

### TypeScript Conventions

- `type` preferred for data shapes/unions; `interface` acceptable for component props
- TS 5.9 with `noEmit` — Babel transpiles, TypeScript only checks types
- `skipLibCheck: true`, `isolatedModules: true`

### React Patterns

- Functional components only, no class components
- Export via `memo()` where appropriate
- Hooks with `use` prefix, memoize with `useCallback`/`useMemo`
- `react-i18next` `useTranslation()` for user-facing strings (**error** in client)
- State: Redux (`@reduxjs/toolkit`) in client, `react-query` v3 + context in submodules
- Styling: `@emotion/css`, MUI `styled()`, or MUI `sx` prop — match surrounding code

### Naming Conventions

| Element          | Convention                   | Example            |
| ---------------- | ---------------------------- | ------------------ |
| React components | PascalCase                   | `ScenarioCard.tsx` |
| Hooks            | camelCase with `use` prefix  | `useSettings.ts`   |
| Utilities        | camelCase                    | `navigation.tsx`   |
| Test files       | `*.test.tsx` or `*-test.tsx` | `utils-test.ts`    |
| Directories      | camelCase                    | `scenarios/`       |

### Error Handling (Frontend)

- Try/catch in hooks and async operations
- `console.warn` for non-critical issues
- Handle errors locally — no global error utility in submodules
- Axios errors caught in calling code

## Project Structure

```
build.sbt                       # Root SBT build (all Scala modules)
.scalafmt.conf                  # Scala formatting config
.scalafix.conf                  # Scalafix rules
designer/
  client/                       # Host React app (Redux, Cypress E2E)
  submodules/                   # Remote React modules (Module Federation)
  server/                       # Designer Scala backend
scenario-compiler/              # Scenario compilation engine
common-api/                     # Shared API types
e2e-tests/                      # End-to-end Scala tests
```
