describe("Expression suggester", () => {
  const seed = "suggester"

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  it("should display colorfull and sorted completions", () => {
    cy.visitNewProcess(seed, "variables")
    cy.layoutScenario()
    cy.get("[model-id=kafka-string]").trigger("dblclick")
    cy.get("[data-testid=window]").as("modal")
    cy.get("[title=value]").next().find(".ace_editor").click().type(".").contains(/\.$/)
    cy.get(".ace_autocomplete").should("be.visible").matchImage({
      maxDiffThreshold: 0.0025,
      screenshotConfig: {padding: [25, 1, 1]},
    })
    cy.get("[title=value]").next().find(".ace_editor").click().type("c").contains(/\.c$/)
    cy.get(".ace_autocomplete").should("be.visible").matchImage({
      maxDiffThreshold: 0.0025,
      screenshotConfig: {padding: [25, 1, 1]},
    })
  })

  it("should display completions for second line (bugfix)", () => {
    cy.visitNewProcess(seed, "variables")
    cy.layoutScenario()
    cy.get("[model-id=kafka-string]").trigger("dblclick")
    cy.get("[data-testid=window]").as("modal")
    cy.get("[title=value]").next().find(".ace_editor").click().type("{enter}#").contains(/^#$/m)
    cy.get(".ace_autocomplete").should("be.visible")
      .matchImage({
        maxDiffThreshold: 0.0025,
        screenshotConfig: {padding: [45, 1, 1]},
      })
  })
})
