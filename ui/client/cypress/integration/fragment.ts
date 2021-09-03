const seed = "fragment"

describe("Fragment", () => {
  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  beforeEach(() => {
    cy.visitNewFragment(seed, "fragment").as("fragmentName")
  })

  describe("input", () => {
    beforeEach(() => {
      cy.get("[model-id=input]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=window]").should("be.visible").as("window")
    })

    it("should display details in modal", () => {
      cy.get("@window").toMatchImageSnapshot()
    })
  })
})
