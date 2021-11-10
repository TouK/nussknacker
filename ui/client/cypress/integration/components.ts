describe("Components list", () => {
  beforeEach(() => {
    cy.visit("/customtabs/components")
  })

  //TODO: It's just temporary tests
  it("should present request1-source component", () => {
    cy.contains(/^Show only used$/i).should("be.visible")
  })
})
