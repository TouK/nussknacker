describe("Components list", () => {
  const seed = "components"

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
    cy.createTestProcess(seed, "testProcess2")
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  beforeEach(() => {
    cy.viewport(1400, 1000)
    cy.visit("/customtabs/components")
  })

  it("should display component", () => {
    cy.contains(/^name$/i).should("be.visible")
    cy.contains(/^categories$/i).should("be.visible")
    cy.contains(/^accountService$/).should("be.visible")
    cy.get("#app-container").toMatchImageSnapshot()
  })

  it("should have dynamic page size", () => {
    cy.contains(/^1-12 of \d+$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 13)
    cy.viewport(1400, 500)
    cy.contains(/^1-3 of \d+$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 4)
  })

  it("should allow filtering by name", () => {
    cy.contains(/^name$/i).parent().find("input").type("response")
    cy.contains(/^response-sink$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 2)
    cy.contains(/^name$/i).parent().find("input").type("-dummy")
    cy.get("[role=row]").should("have.lengthOf", 1)
  })

  it("should allow filtering by group", () => {
    cy.contains(/^group$/i).parent().as("select")
    cy.get("[role=row]").should("have.length.greaterThan", 11)
    cy.get("@select").click()
    cy.get("[role=option]").as("options")
    cy.get("@options").should("have.lengthOf", 7)
    cy.get("@options").contains(/^base/i).click()
    cy.get("@options").contains(/^types/i).click()
    cy.get("[role=row]").should("have.lengthOf", 11)
    cy.get("body").click()
    cy.get("@select").contains(/^base/).dblclick()
    cy.get("[role=row]").should("have.lengthOf", 6).contains(/^filter/).should("be.visible")
    cy.get("@select").find("[data-testid=CancelIcon]").click()
    cy.get("[role=row]").should("have.length.greaterThan", 2)
  })

  it("should allow filtering by category", () => {
    cy.contains(/^category/i).parent().as("select")
    cy.contains(/^name$/i).parent().find("input").type("source")
    cy.get("[role=row]").should("have.lengthOf", 9)
    cy.get("@select").click()
    cy.get("[role=option]").should("have.lengthOf", 5).as("options")
    cy.get("@options").contains(/^demo/i).click()
    cy.get("[role=row]").should("have.lengthOf", 2)
    cy.get("@options").contains(/^server/i).click()
    cy.get("[role=row]").as("rows").should("have.lengthOf", 3)
    cy.get("@rows").filter(`:contains("DemoFeatures")`).should("have.lengthOf", 1)
    cy.get("@rows").filter(`:contains("Server")`).should("have.lengthOf", 1)
  })

    /*
  test("should allow filtering by usage", () => {
    cy.contains(/of 90$/).should("be.visible")
    cy.contains(/^Show used only$/).click()
    cy.contains(/of 2$/).should("be.visible")
    cy.contains(/^Show unused only$/).click()
    cy.contains(/of 88$/).should("be.visible")
  })  */
})
