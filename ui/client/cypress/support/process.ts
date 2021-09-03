import {jsonToBlob} from "./tools"
import Chainable = Cypress.Chainable

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable<Subject = any> {
      createTestProcess: typeof createProcess,
      deleteTestProcess: typeof deleteTestProcess,
      getTestProcesses: typeof getTestProcesses,
      deleteAllTestProcesses: typeof deleteAllTestProcesses,
      createTestProcessName: typeof createTestProcessName,
      createTestFragment: typeof createProcess,
      importTestProcess: typeof importTestProcess,
      visitNewProcess: typeof visitNewProcess,
      visitNewFragment: typeof visitNewFragment,
      postFormData: typeof postFormData,
      visitProcess: typeof visitProcess,
    }
  }
}

function createTestProcessName(name?: string) {
  return cy.wrap(`${Cypress.env("processNamePrefix")}-${Date.now()}-${name}-test-process`)
}

function createProcess(name?: string, fixture?: string, category = "Category1", isSubprocess?: boolean) {
  return cy.createTestProcessName(name).then(processName => {
    let url = `/api/processes/${processName}/${category}`
    if (isSubprocess) {
      url += "?isSubprocess=true"
    }
    cy.request({method: "POST", url}).its("status").should("equal", 201)
    return fixture ? cy.importTestProcess(processName, fixture) : cy.wrap(processName)
  })
}

const createTestProcess = (name?: string, fixture?: string, category = "Category1") => createProcess(name, fixture, category)

const createTestFragment = (name?: string, fixture?: string, category = "Category1") => createProcess(name, fixture, category, true)

function visitProcess(processName: string) {
  cy.visit(`/visualization/${processName}`)
  cy.wait("@fetch").its("response.statusCode").should("eq", 200)
  return cy.wrap(processName)
}

function visitNewProcess(name?: string, fixture?: string, category?: string) {
  cy.intercept("GET", "/api/processes/*").as("fetch")
  return cy.createTestProcess(name, fixture, category).then(processName => {
    return cy.visitProcess(processName)
  })
}

function visitNewFragment(name?: string, fixture?: string, category?: string) {
  cy.intercept("GET", "/api/processes/*").as("fetch")
  return cy.createTestFragment(name, fixture, category).then(processName => {
    return cy.visitProcess(processName)
  })
}

function deleteTestProcess(processName: string, force?: boolean) {
  const url = `/api/processes/${processName}`

  function getRequest() {
    return cy.request({method: "DELETE", url, failOnStatusCode: false})
  }

  return getRequest()
    .then(response => force && response.status === 409 ?
      cy.request({method: "POST", url: `/api/processManagement/cancel/${processName}`, failOnStatusCode: false}).then(getRequest) :
      cy.wrap(response))
    .its("status").should("be.oneOf", [200, 404])
}

function postFormData(url: string, auth: {username: string, password: string}, body?: FormData): Chainable {
  const {password, username} = auth
  const authorization = `Basic ${btoa(`${username}:${password}`)}`
  return cy.wrap(new Cypress.Promise((resolve, reject) => {
    fetch(url, {
      method: "POST",
      headers: {authorization},
      body,
    })
      .then(res => res.json())
      .then(resolve, reject)
  }))
}

function importTestProcess(name: string, fixture = "testProcess") {
  return cy.fixture(fixture).then(json => {
    const formData = new FormData()
    const blob = jsonToBlob({...json, metaData: {...json.metaData, id: name}})
    formData.set("process", blob, "data.json")
    const auth = {
      username: Cypress.env("testUserUsername"),
      password: Cypress.env("testUserPassword"),
    }
    return cy.postFormData(`/api/processes/import/${name}`, auth, formData)
  }).then((process) => {
    cy.request("PUT", `/api/processes/${name}`, {comment: "import test data", process})
    return cy.wrap(name)
  })
}

function getTestProcesses(filter?: string) {
  const url = `/api/processes`
  return cy.request({url})
    .then(({body}) => body
      .filter(({id}) => id.includes(filter || Cypress.env("processName")))
      .map(({id}) => id))
}

function deleteAllTestProcesses({filter, force}: {filter?: string, force?: boolean}) {
  return cy.getTestProcesses(filter).each((id: string) => {
    cy.deleteTestProcess(id, force)
  })
}

Cypress.Commands.add("createTestProcess", createTestProcess)
Cypress.Commands.add("deleteTestProcess", deleteTestProcess)
Cypress.Commands.add("getTestProcesses", getTestProcesses)
Cypress.Commands.add("deleteAllTestProcesses", deleteAllTestProcesses)
Cypress.Commands.add("createTestProcessName", createTestProcessName)
Cypress.Commands.add("createTestFragment", createTestFragment)
Cypress.Commands.add("importTestProcess", importTestProcess)
Cypress.Commands.add("visitNewProcess", visitNewProcess)
Cypress.Commands.add("visitNewFragment", visitNewFragment)
Cypress.Commands.add("postFormData", postFormData)
Cypress.Commands.add("visitProcess", visitProcess)
