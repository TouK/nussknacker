const seed = "counts"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses(seed)
  })

  after(() => {
    cy.deleteAllTestProcesses(seed)
  })

  beforeEach(() => {
    cy.createTestProcess(seed).then(processName => {
      cy.importTestProcess(processName)
      cy.visit(`/visualization/${processName}?businessView=false`)
    })
    cy.contains(/^counts$/i).as("button")
  })

  it("should have counts button and modal", () => {
    cy.get("@button").should("be.visible").toMatchImageSnapshot()
    cy.get("@button").click()
    cy.get("[data-testid=modal]").should("be.visible").toMatchImageSnapshot()
  })
})
