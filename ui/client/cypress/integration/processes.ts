const NAME = "process-list"

describe("Processes list", () => {
  before(() => {
    cy.deleteAllTestProcesses(NAME)
    cy.createTestProcessName(NAME).as("processName")
  })

  after(() => {
    cy.deleteAllTestProcesses(NAME)
  })

  beforeEach(() => {
    cy.visit("/")
    cy.url().should("match", /processes/)
  })

  it("should have no process matching filter", () => {
    cy.get("[placeholder='Filter by text...']").type(NAME)
    cy.contains(/^No matching records found.$/i).should("be.visible")
  })

  it("should allow creating new process", function() {
    cy.contains(/^create new process$/i).should("be.visible").click()
    cy.get("#newProcessId").type(this.processName)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.url().should("contain", `visualization\/${this.processName}`)
  })

  it("should have test process on list", function() {
    cy.get("[placeholder='Filter by text...']").type(NAME)
    cy.get("tbody tr").should("have.length", 1).within(() => {
      cy.get("input").should("have.value", this.processName)
      cy.get("[label=Edit] a").should("have.attr", "href")
    })
  })
})
