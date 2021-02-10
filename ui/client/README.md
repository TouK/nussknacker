# Prerequisites

Make sure you have relatively new nodejs/npm version (see `.nvmrc` for details)

# Run

```
npm ci && npm start
open http://localhost:3000
```

# Tests

## Unit (jest) tests

```npm test```

### Run tests from IntelliJ

`Jest` should work out of the box, just click green arrow.

## E2E (cypress) tests
_You should copy and fill `cypress/fixtures/env.json.template` into `cypress/fixtures/env.json` before start._

### Start server and test (CI)
```npm test:e2e:ci```

### CLI test with running devServer
```npm test:e2e```

### GUI test with running devServer
```npm test:e2e:dev```
