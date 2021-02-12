import {jsonToBlob} from "./tools"
import Chainable = Cypress.Chainable

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable<Subject = any> {
      createTestProcess: typeof createTestProcess,
      deleteTestProcess: typeof deleteTestProcess,
      getTestProcesses: typeof getTestProcesses,
      deleteAllTestProcesses: typeof deleteAllTestProcesses,
      createTestProcessName: typeof createTestProcessName,
      importTestProcess: typeof importTestProcess,
      postFormData: typeof postFormData,
    }
  }
}

function createTestProcessName(name?: string) {
  return cy.wrap(`${Cypress.env("processNamePrefix")}-${Date.now()}-${name}-test-process`)
}

function createTestProcess(processName?: string) {
  return cy.createTestProcessName(processName).then(name => {
    const url = `/api/processes/${name}/Category1`
    return cy.request({method: "POST", url}).its("status").should("equal", 201).then(() => {
      return cy.wrap(name)
    })
  })
}

function deleteTestProcess(name: string) {
  const url = `/api/processes/${name}`
  return cy.request({method: "DELETE", url, failOnStatusCode: false})
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

function importTestProcess(name: string) {
  return cy.fixture("testProcess").then(json => {
    const formData = new FormData()
    const blob = jsonToBlob({...json, metaData: {...json.metaData, id: name}})
    formData.set("process", blob, "data.json")
    return cy.postFormData(`/api/processes/import/${name}`, Cypress.env("testUser"), formData)
  }).then((process) => {
    return cy.request("PUT", `/api/processes/${name}`, {comment: "import test data", process})
  })
}

function getTestProcesses(filter?: string) {
  const url = `/api/processes`
  return cy.request({url})
    .then(({body}) => body
      .filter(({id}) => id.includes(filter || Cypress.env("processName")))
      .map(({id}) => id))
}

function deleteAllTestProcesses(filter?: string) {
  return cy.getTestProcesses(filter).each((id: string) => {
    cy.deleteTestProcess(id)
  })
}

Cypress.Commands.add("createTestProcess", createTestProcess)
Cypress.Commands.add("deleteTestProcess", deleteTestProcess)
Cypress.Commands.add("getTestProcesses", getTestProcesses)
Cypress.Commands.add("deleteAllTestProcesses", deleteAllTestProcesses)
Cypress.Commands.add("createTestProcessName", createTestProcessName)
Cypress.Commands.add("importTestProcess", importTestProcess)
Cypress.Commands.add("postFormData", postFormData)
