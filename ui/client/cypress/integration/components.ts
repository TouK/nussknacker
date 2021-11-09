describe("Components list", () => {
  beforeEach(() => {
    cy.visit("/customtabs/components")
  })

  it("should present request1-source component", () => {
    cy.contains(/^request1-source$/i).should("be.visible")
  })
})
