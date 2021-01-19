import {deleteTestProcess, getProcessName, getTestProcesses} from "../support/tools"

describe("Processes list", () => {
  let processName: string

  before(() => {
    getTestProcesses().should(processes => processes.map(deleteTestProcess))
    processName = getProcessName()
  })

  beforeEach(() => {
    cy.fixture("env").then((env) => {
      const {BASIC_AUTH} = env
      cy.visit("/", {auth: BASIC_AUTH})
    })
    cy.url().should("match", /processes/)
  })

  after(() => {
    deleteTestProcess(processName)
  })

  it("should have no process matching filter", () => {
    cy.get("[placeholder='Filter by text...']").type("cypress")
    cy.contains(/^No matching records found.$/i).should("be.visible")
  })

  it("should allow creating new process", () => {
    cy.contains(/^create new process$/i).should("be.visible").click()
    cy.get("#newProcessId").type(processName)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.url().should("contain", `visualization\/${processName}`)
  })

  it("should have test process on list", () => {
    cy.get("[placeholder='Filter by text...']").type("cypress")
    cy.get("tbody tr").should("have.length", 1).within(() => {
      cy.get("input").should("have.value", processName)
      cy.get("[label=Edit] a").should("have.attr", "href")
    })
  })
})
