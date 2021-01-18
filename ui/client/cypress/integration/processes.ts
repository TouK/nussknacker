import {BASIC_AUTH, HOST} from "../fixtures/env.json"
import {deleteTestProcess, getProcessName} from "../support/tools"

let processName: string

describe("Processes list", () => {
  before(() => {
    cy.request({
      url: `${HOST}/api/processes`,
      auth: BASIC_AUTH,
    }).should(({body}) => body.filter(({id}) => id.includes("cypress")).map(({id}) => deleteTestProcess(id)))
    processName = getProcessName()
  })

  beforeEach(() => {
    cy.visit(HOST, {auth: BASIC_AUTH})
    cy.url().should("match", /processes/)
  })

  after(() => {
    deleteTestProcess(processName)
  })

  it("should have no process matching filter", () => {
    cy.get("[placeholder='Filter by text...']").type(processName)
    cy.contains(/^No matching records found.$/i).should("be.visible")
  })

  it("should allow creating new process", () => {
    cy.contains(/^create new process$/i).should("be.visible").click()
    cy.get("#newProcessId").type(processName)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.url().should("contain", `visualization\/${processName}`)
  })

  it("should have test process on list", () => {
    cy.get("[placeholder='Filter by text...']").type(processName)
    cy.get("tbody tr").should("have.length", 1).within(() => {
      cy.get("input").should("have.value", processName)
      cy.get("[label=Edit] a").should("have.attr", "href")
    })
  })
})
