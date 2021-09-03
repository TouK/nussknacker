describe("Creator toolbar", () => {
  const seed = "creator"

  before(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  beforeEach(() => {
    cy.viewport("macbook-15")
    cy.visitNewProcess(seed).as("processName")
    cy.contains(/^Creator panel.*sources/i).as("toolbar")
  })

  it("should allow filtering", () => {
    cy.get("@toolbar").should("be.visible").toMatchImageSnapshot()
    cy.get("@toolbar").find("input").type("var")
    cy.get("@toolbar").toMatchImageSnapshot()
  })
})
