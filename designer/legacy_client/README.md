# Prerequisites

## NodeJS and NPM 

Make sure you have relatively new nodejs/npm version (see `.nvmrc` for details). To make sure that you use correct version, use:
```
nvm use
```

## Package dependencies

Fetch locked package dependencies using:
```
npm ci 
```

# Run

You can run frontend connected to one of different backends, depending on your needs. After each "run" command, frontend will be available at http://localhost:3000. 

Below there are described possible options. 

After you execute any of them, you probably want to run also `submodules` as described [here](#Submodules). This step is optional, but most of the application's functionality relies on it. 

## Using backend started with Docker

This option requires Docker and connection to DockerHub registry. It will use `touk/nussknacker` image in `staging-latest` version.
```
npm run start:backend-staging
```

## Using locally started backend

This option uses backend started at http://localhost:8080 . For more information how to start it, take a look at [Backend instruction](../../CONTRIBUTING.md#running) 
```
npm start
```

## Using external environment

You can also run frontend connected to external environment like https://demo.nussknacker.io . To do that just invoke:
```
npm run start:backend-demo
```
For more options take a look at `package.json`

# Tests

## Unit (jest) tests

```
npm test
```

### Run tests from IntelliJ

`Jest` should work out of the box, just click play button.

## E2E (cypress) tests

Background: Cypress is a framework for end-to-end frontend tests. It verifies correctness of results using captured image snapshots.
It runs tests in browser connected to our frontend application at http://localhost:3000. It uses some variables available
in `cypress.env.json` like credentials.

> WARNING: Image snapshots are **OS** and even **resolution** dependent! Please add image snapshots captured on unified environment.

### Run cypress tests

There are a few ways to run cypress tests.

#### Using cypress devServer

Before this option you should run backend and frontend - take a look at "Run" chapter.

After execution of:
```
npm run test:e2e:dev
```
you will see cypress devServer. It is useful to see what is done in each test, but it has some drawbacks:
- "Run all specs" option doesn't work correctly
- You can't invoke test case separately without changing file with scenario - you have to change "it" -> "xit" to exclude or "it" -> "it.only" to test separately 
- Produced image snapshots are OS dependent, so you shouldn't use it for purpose of changing captured image snapshots!
  
#### Using devServer in CLI

This option is similar to option above, but run all tests in CLI and the same - you shouldn't use it for purpose of changing captured image snapshots!
```
npm run test:e2e
```

#### Using Intellij Idea

Thanks to [Cypress Plugin](https://plugins.jetbrains.com/plugin/13819-cypress-support) you can run tests by just clicking play button.
If you want to see, what cypress do, you can turn "Headed" mode on.

Just like in options above, you should run backend and frontend before and the same - you shouldn't use it for purpose of changing captured image snapshots!

#### Using unified linux environment 

This is the correct option if you want to add/modify image snapshots and make sure that it was done in deterministic way.
It runs backend in Docker container, frontend connected to this backend and after that it runs cypress tests also in Docker container.

You should run once: 
```
npm run start:backend-docker
```

and after that you can run multiple times:
```
npm run test:e2e:linux
```

#### Using unified linux environment with update image snapshots mode enabled

To run cypress test in mode that would update image snapshots, use the same commands as in the option above but with `:update` suffix:
```
npm run test:e2e:linux:update
```

#### Submodules 
Independent parts of application e.g. scenarios and components tabs

To render views using submodules in dev mode you need to run submodules app in dev mode as well (available on port 5001). 
Assuming that core frontend is running on `localhost:3000` as described [here](#Run), to make it happen just: 
```
cd ../submodules
NU_FE_CORE_URL=http://localhost:3000 npm start
```
> WARNING: When using **unified linux environment** prefix npm start invocation with NU_FE_CORE_URL=http://host.docker.internal:3000 and add entry in `/etc/hosts` leading to `127.0.0.1`

### Fixing cypress tests

After some changes in frontend it might be needed to rewrite captured image snapshots. The easiest way is to:
1. Run cypress tests using "Run cypress tests using unified linux environment with update image snapshots mode enabled" method
2. Review changes in files e.g. using Intellij Idea changes diff
3. Commit changes or do fixes in scenarios.

# Internationalization

We use `react-i18next` package for internalizations. This mechanism has priority of determinining value for given key (`i18next.t(key, default)`):
1. label from `translations/$lng/main.json` which is served at `/assets/locales/$lng/main.json` resource
2. `default` passed as an argument

File `translations/$lng/main.json` is generated in `prebuild` phase based on defaults. During development (`start` scripts) is removed to avoid confusions.
