import {defaultsDeep} from "lodash"
import UAParser from "ua-parser-js"

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable<Subject = any> {
      // fixed wrong original
      toMatchImageSnapshot(options?: Partial<{
        name: string,
        imageConfig: Partial<{
          createDiffImage: boolean,
          threshold: number,
          thresholdType: "percent" | "pixel",
        }>,
        screenshotConfig: Partial<ScreenshotDefaultsOptions>,
      }>): Chainable<null>,

      toMatchExactImageSnapshot(): Chainable<null>,
    }
  }
}

Cypress.Commands.add("toMatchExactImageSnapshot", {prevSubject: true}, (subject) => cy
  .wrap(subject)
  .should("be.visible")
  .toMatchImageSnapshot({
    imageConfig: {
      threshold: 0.00001,
    },
  }))

const getRequestOptions = (...args): Partial<Cypress.RequestOptions> => {
  const [first, second, third] = args
  return typeof first === "string" ?
    typeof second === "string" ?
      {method: first, url: second, body: third} :
      {url: first, body: second} :
    first
}

Cypress.Commands.overwrite("request", (original: Cypress.Chainable["request"], ...args) => original({
  auth: {
    username: Cypress.env("testUserUsername"),
    password: Cypress.env("testUserPassword"),
  },
  ...getRequestOptions(...args),
}))
Cypress.Commands.overwrite("visit", (original: Cypress.Chainable["visit"], first, second) => {
  const auth = {
    username: Cypress.env("testUserUsername"),
    password: Cypress.env("testUserPassword"),
  }

  const {name: os} = new UAParser().getOS()
  const pixelRatio = window.devicePixelRatio
  Cypress.env(defaultsDeep({
    "cypress-plugin-snapshots": {
      separator: pixelRatio !== 1 ? ` [${os} x${pixelRatio}] #` : ` [${os}] #`,
    },
  }, Cypress.env()))
  return original(typeof first === "string" ? {auth, ...second, url: first} : {auth, ...first})
})
