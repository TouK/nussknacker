describe("Fragment", () => {
  const seed = "fragment"
  const screenshotConfig = {
    blackout: [
      ".graphPage > :not(#nk-graph-main) > div",
    ],
  }

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

  describe.only("used in scenario", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed, "testProcess")
    })

    it("should display fragment graph in modal", () => {
      cy.contains("layout").click()
      cy.contains("fragments").should("be.visible").click()
      cy.contains("fragment-test")
        .should("be.visible")
        .drag("#nk-graph-main", {x: 580, y: 450, position: "right", force: true})

      cy.contains("layout").click()

      cy.get("[model-id$=-fragment-test-process]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=window]").contains(/^cancel$/i).click()
      cy.get("#nk-graph-main").toMatchImageSnapshot({screenshotConfig})
    })

  })
})
