describe("Process", () => {
  const seed = "process"
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

  describe("with data", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed, "testProcess")
    })

    it("should allow editing switch edge expression", () => {
      cy.contains(/^layout$/).click()
      cy.contains(/^base$/).should("be.visible").click()
      cy.contains(/^switch$/)
        .should("be.visible")
        .drag("#nk-graph-main", {x: 580, y: 450, position: "right", force: true})
      cy.contains(/^layout$/).click()
      cy.get("[model-id$=switch-sendSms-true]").should("be.visible").trigger("dblclick")

      cy.get("[data-testid=window]").should("be.visible").as("edgeWindow")
      cy.get("@edgeWindow").find(".ace_editor").as("input")
      cy.get("@input").click().type(" || false")
      cy.contains(/^apply/i).should("be.enabled").click()
      cy.get("[data-testid=window]").should("not.exist")
      cy.get("#nk-graph-main").toMatchImageSnapshot({screenshotConfig})
    })
  })
})
