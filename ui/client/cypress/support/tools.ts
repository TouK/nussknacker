const processName = "e2e-test-cypress-process"

export function getTestProcesses(filter = "cypress"): Cypress.Chainable {
  return cy.fixture("env").then(({BASIC_AUTH}) => {
    return cy.request({
      url: `/api/processes`,
      auth: BASIC_AUTH,
    }).then(({body}) => body.filter(({id}) => id.includes(filter)).map(({id}) => id))
  })
}

export function createTestProcess(name = processName): Cypress.Chainable {
  return cy.fixture("env").then(({BASIC_AUTH}) => {
    return cy.request({
      url: `/api/processes/${name}/Category1`,
      method: "POST",
      auth: BASIC_AUTH,
    }).should(response => {
      expect(response.status).to.eq(201)
    })
  })
}

export function deleteTestProcess(name = processName): Cypress.Chainable {
  return cy.fixture("env").then(({BASIC_AUTH}) => {
    return cy.request({
      url: `/api/processes/${name}`,
      method: "DELETE",
      auth: BASIC_AUTH,
      failOnStatusCode: false,
    }).should(response => {
      expect(response.status).to.be.oneOf([200, 404])
    })
  })
}

export const jsonToBlob = (data: any) => new Blob([JSON.stringify(data)], {type: "application/json"})

export const getProcessName = () => `e2e-${Date.now()}-cypress-test-process`
