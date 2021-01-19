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

```npm test:e2e``` or ```npm test:e2e:dev```

_You should copy and fill `cypress/fixtures/env.json.template` into `cypress/fixtures/env.json` before start._

# Background

As a initial template was used todomvc example from https://github.com/gaearon/redux-devtools (commit
619a18b26a5482585b10eddd331ccacf582ba913)
It contains:

- react in version 15.0.1
- redux in version 3.1.1
- application embraces [react-devtools-extension](https://github.com/zalmoxisus/redux-devtools-extension)
- react-hot-loader in version 3.0.0-beta.1
