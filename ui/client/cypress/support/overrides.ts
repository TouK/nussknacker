import {defaultsDeep} from "lodash"

const getRequestOptions = (...args): Partial<Cypress.RequestOptions> => {
  const [first, second, third] = args
  return typeof first === "string" ?
    typeof second === "string" ?
      {method: first, url: second, body: third} :
      {url: first, body: second} :
    first
}

Cypress.Commands.overwrite("request", (original: Cypress.Chainable["request"], ...args) => original({
  auth: Cypress.env("testUser"),
  ...getRequestOptions(...args),
}))

Cypress.Commands.overwrite("visit", (original: Cypress.Chainable["visit"], first, second) => {
  const auth = Cypress.env("testUser")
  const pixelRatio = window.devicePixelRatio
  Cypress.env(defaultsDeep({
    "cypress-plugin-snapshots": {
      separator: pixelRatio !== 1 ? ` [x${pixelRatio}] #` : " #",
    },
  }, Cypress.env()))
  return original(typeof first === "string" ? {auth, ...second, url: first} : {auth, ...first})
})
