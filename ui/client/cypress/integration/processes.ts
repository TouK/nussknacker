describe.skip("Processes list", () => {
  const NAME = "process-list"

  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
    cy.createTestProcessName(NAME).as("processName")
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visit("/")
    cy.url().should("match", /scenarios/)
    cy.get("[placeholder='Search...']", {timeout: 60000}).should("be.visible")
    cy.contains(/^loading.../i, {timeout: 60000}).should("not.exist")
  })

  it("should have no process matching filter", () => {
    cy.get("[placeholder='Search...']").type(NAME)
    cy.contains(/^none of the|^the list is empty$/i).should("be.visible")
  })

  it("should allow creating new process", function() {
    cy.contains(/^new scenario$/i).should("be.visible").click()
    cy.get("#newProcessId", {timeout: 30000}).type(this.processName)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.url().should("contain", `visualization\/${this.processName}`)
  })

  it("should have test process on list", function() {
    cy.get("[placeholder='Search...']").type(NAME)
    cy.url().should("contain", NAME)
    cy.contains(/^every of the|^1 of the/i).should("be.visible")
    cy.wait(200) // wait for highlight
    cy.contains(this.processName).should("be.visible")//.toMatchImageSnapshot() FIXME
    cy.contains(this.processName).click({x: 10, y: 10})
    cy.url().should("contain", `visualization\/${this.processName}`)
  })
})

describe("Processes list (new table)", () => {
  const NAME = "process-list"

  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
    cy.createTestProcessName(NAME).as("processName")
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visit("/")
    cy.url().should("match", /scenarios/)
    cy.get("[placeholder='Search...']", {timeout: 60000}).should("be.visible")
  })

  it("should have no process matching filter", () => {
    cy.get("[placeholder='Search...']").type(NAME)
    cy.contains(/^no rows$/i).should("be.visible")
  })

  it("should allow creating new process", function () {
    cy.contains(/^new scenario$/i).should("be.visible").click()
    cy.get("#newProcessId", {timeout: 30000}).type(this.processName)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.url().should("contain", `visualization\/${this.processName}`)
  })

  it("should have test process on list", function () {
    cy.get("[placeholder='Search...']").type(NAME)
    cy.url().should("contain", NAME)
    cy.wait(200) // wait for highlight
    cy.contains(this.processName).should("be.visible")
    cy.get("#app-container").toMatchImageSnapshot()
    cy.contains(this.processName).click({x: 10, y: 10})
    cy.url().should("contain", `visualization\/${this.processName}`)
  })
})

describe("Processes list (legacy)", () => {
  const NAME = "process-list"

  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
    cy.createTestProcessName(NAME).as("processName")
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visit("/processes")
    cy.url().should("match", /processes/)
  })

  it("should have no process matching filter", () => {
    cy.get("[placeholder='Filter by text...']").type(NAME)
    cy.contains(/^No matching records found.$/i).should("be.visible")
  })

  it("should allow creating new process", function() {
    cy.contains(/^create new scenario$/i).should("be.visible").click()
    cy.get("#newProcessId").type(this.processName)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.url().should("contain", `visualization\/${this.processName}`)
  })

  it("should have test process on list", function() {
    cy.get("[placeholder='Filter by text...']").type(NAME)
    cy.get("tbody tr").should("have.length", 1).within(() => {
      cy.contains(this.processName).should("be.visible")
      cy.get("[label=Edit] a").should("have.attr", "href")
    })
  })
})
