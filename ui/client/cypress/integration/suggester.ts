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
    cy.get("[title=value]").next().find(".ace_editor").as("input")
    cy.get("@input").click().type(".")
    // TODO: make this snapshot checking more deterministic
    // cy.get("@modal").toMatchExactImageSnapshot()
    cy.get("@input").type("c")
    // cy.get("@modal").toMatchExactImageSnapshot()
  })
})
