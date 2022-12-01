describe("Node window", () => {
  const NAME = "node-window"

  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visitNewProcess(NAME).as("processName")
  })

  it("should display periodic source", () => {
    cy.viewport(1500, 800)

    cy.contains(/^sources$/)
      .should("be.visible").click()
    cy.get("[data-testid='component:periodic']")
      .should("be.visible")
      .drag("#nk-graph-main", {x: 800, y: 300, position: "right", force: true})

    cy.getNode("periodic").dblclick()

    cy.get("[data-testid=window]").should("be.visible")
    cy.contains(/^hours$/).should("be.visible")
    cy.get("[data-testid=window]").matchImage({screenshotConfig: {padding: 16}})
  })
}
