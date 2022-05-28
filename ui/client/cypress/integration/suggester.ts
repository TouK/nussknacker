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
    cy.contains(/^layout$/).click()
    cy.get("[model-id=kafka-string]").trigger("dblclick")
    cy.get("[data-testid=window]").as("modal")
    cy.get("[title=value]").next().find(".ace_editor").click().type(".").wait(100)
    cy.get(".ace_autocomplete").should("be.visible").toMatchExactImageSnapshot({screenshotConfig: {padding: [40, 8, 8]}})
    cy.get("[title=value]").next().find(".ace_editor").click().type("c", {force: true}).wait(100)
    cy.get(".ace_autocomplete").should("be.visible").toMatchExactImageSnapshot({screenshotConfig: {padding: [40, 8, 8]}})
  })
})
