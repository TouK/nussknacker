import {BASIC_AUTH, HOST} from "../fixtures/env.json"

const processName = "e2e-test-cypress-process"

export const createTestProcess = (name = processName) => {
  cy.request({
    url: `${HOST}/api/processes/${name}/Category1`,
    method: "POST",
    auth: BASIC_AUTH,
  }).should(response => {
    expect(response.status).to.eq(201)
  })
}

export const deleteTestProcess = (name = processName) => cy.request({
  url: `${HOST}/api/processes/${name}`,
  method: "DELETE",
  auth: BASIC_AUTH,
  failOnStatusCode: false,
}).should(response => {
  expect(response.status).to.be.oneOf([200, 404])
})

export const jsonToBlob = (data: any) => new Blob([JSON.stringify(data)], {type: "application/json"})

export const getProcessName = () => `e2e-${Date.now()}-cypress-test-process`
