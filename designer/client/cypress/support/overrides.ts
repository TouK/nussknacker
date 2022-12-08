import {defaultsDeep} from "lodash"
import UAParser from "ua-parser-js"

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    //looks like it should be available
    //used in with drag from @4tw/cypress-drag-drop to force drop position
    interface ClickOptions {
      x: number,
      y: number,
    }
  }
}

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
  const osDir = pixelRatio !== 1 ? `${os}/x${pixelRatio}` : os
  Cypress.env(defaultsDeep({
    pluginVisualRegressionImagesPath: `{spec_path}/__image_snapshots__/${Cypress.browser.name}/${osDir}`,
  }, Cypress.env()))
  return original(typeof first === "string" ? {auth, ...second, url: first} : {auth, ...first})
})
