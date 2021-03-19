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
_You should copy and fill `cypress.env.json.template` into `cypress.env.json` before start._

##### Image snapshots are **OS** and even resolution dependent!

Compare, update (when needed) and commit **image snapshots** made with `BACKEND_DOMAIN` set to url of backend started with `npm run start-backend:docker`

#### Start server and test (CI) with already running BE

```npm test:e2e:ci```

#### Start BE, start FE server and test inside linux image (snapshots for **CI**) 

```npm test:e2e:docker```

#### CLI test with running devServer
```npm test:e2e```

#### GUI test with running devServer
```npm test:e2e:dev```
