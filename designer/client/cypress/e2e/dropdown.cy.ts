describe("Dropdown", () => {
  const seed = "dropdown"

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  it("should display menu portal", () => {
    cy.visitNewProcess(seed, "testProcess")
    cy.layoutScenario()
    cy.get("[model-id=enricher]").should("be.visible").trigger("dblclick")
    cy.get("[data-testid=window]").should("be.visible")
    cy.get("div[class$=singleValue").contains("normal").parent().click()
    cy.get("[data-testid=window]").matchImage()
    cy.get("div[id$=react-select-2-option-0]").contains("normal").should("be.visible")
    cy.get("div[id$=react-select-2-option-1]").contains("gold").should("be.visible")
      .click()
    //It should be.visible instead of exist, but Cypress think it's covered
    cy.get("div[class$=singleValue").contains("gold").should("exist")
    cy.get("[data-testid=window]").matchImage()
  })
})
