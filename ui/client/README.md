# Prerequisites

To run make sure that you have the newest version of nodejs (currently it is 6.3.0) and npm (currently it is 3.10.3). If you have older
versions follow the insruction
on http://askubuntu.com/questions/594656/how-to-install-the-latest-versions-of-nodejs-and-npm-for-ubuntu-14-04-lts
After that you may need to cleanup npm global modules (result of `sudo npm -g list` should be blank).

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
