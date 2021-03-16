const seed = "creator"

describe("Creator toolbar", () => {
  before(() => {
    cy.deleteAllTestProcesses(seed)
  })

  after(() => {
    cy.deleteAllTestProcesses(seed)
  })

  beforeEach(() => {
    cy.viewport("macbook-15")
    cy.visitNewProcess(seed).as("processName")
    cy.contains(/^Creator panel.*sources/i).as("toolbar")
  })

  it("should allow filtering", () => {
    cy.get("@toolbar").should("be.visible").toMatchImageSnapshot()
    cy.get("@toolbar").find("input").type("kafka")
    cy.get("@toolbar").toMatchImageSnapshot()
  })
})
