Cypress.Screenshot.defaults({
  blackout: [`[data-testid="version-info"]`],
  onBeforeScreenshot: $el => $el.find(`[data-testid="version-info"]`)?.hide(),
  onAfterScreenshot: $el => $el.find(`[data-testid="version-info"]`)?.show(),
})

describe("Components list", () => {
  const seed = "components"

  const baseGroupComponents = 5
  const totalGroups = 7
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

  // We filter by Default category in almost all test cases because in other categories there are sandbox components which
  // will often change and this changes won't have influence on our official distribution

  it("should display component", () => {
    filterByDefaultCategory()
    cy.contains(/^name$/i).should("be.visible")
    cy.contains(/^categories$/i).should("be.visible")
    cy.contains(/^filter$/).should("be.visible")
    cy.get("#app-container").toMatchImageSnapshot()
  })

  it("should have dynamic page size", () => {
    filterByDefaultCategory()
    cy.contains(/^1–12 of \d+$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 13)
    cy.viewport(1400, 500)
    cy.contains(/^1–3 of \d+$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 4)
  })

  it("should allow filtering by name", () => {
    filterByDefaultCategory()
    cy.contains(/^name$/i).parent().find("input").type("filt")
    cy.contains(/^filter$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 2)
    cy.contains(/^name$/i).parent().find("input").type("-dummy")
    cy.get("[role=row]").should("have.lengthOf", 1)
    cy.matchQuery("?CATEGORY=Default&NAME=filt-dummy")
  })

  it("should allow filtering by group", () => {
    filterByDefaultCategory()
    cy.contains(/^group$/i).parent().as("select")
    cy.get("[role=row]").should("have.length.greaterThan", 11)
    cy.get("@select").click()
    cy.get("[role=option]").as("options")
    cy.get("@options").should("have.lengthOf", totalGroups)
    cy.get("@options").contains(/^base/i).click()
    cy.get("@options").contains(/^source/i).click()
    cy.matchQuery("?CATEGORY=Default&GROUP=base&GROUP=sources")
    cy.get("[role=row]").should("have.lengthOf", 11)
    cy.get("body").click()
    cy.get("@select").contains(/^base/).dblclick()
    cy.get("[role=row]").should("have.lengthOf", baseGroupComponents + 1).contains(/^filter/).should("be.visible")
    cy.get("@select").find("[data-testid=CancelIcon]").click()
    cy.get("[role=row]").should("have.length.greaterThan", 2)
  })

  it("should allow filtering by usage", () => {
    filterByDefaultCategory()
    cy.contains(/^Show used only$/).click()
    cy.matchQuery("?CATEGORY=Default&USED_ONLY=true")
    cy.get("#app-container").toMatchImageSnapshot()
    cy.contains(/^Show unused only$/).click()
    cy.matchQuery("?CATEGORY=Default&UNUSED_ONLY=true")
    cy.get("#app-container").toMatchImageSnapshot()
  })

  it("should apply filters from query", () => {
    cy.visit("/customtabs/components?NAME=split&GROUP=base&CATEGORY=Default&CATEGORY=DemoFeatures&UNUSED_ONLY=true")
    cy.contains(/^name$/i).should("be.visible")
    cy.get("[role=row]").should("have.length", 2)
    cy.get("[role=row]").contains(/^Default$/).should("be.visible")
    cy.get("#app-container").toMatchImageSnapshot()
  })

  it("should apply category filters by cell click", () => {
    filterByBaseGroup()
    cy.contains(/^category$/i).should("be.visible")
    cy.get("[role=row]").should("have.length.above", 1)
    cy.get("[role=row]").contains(/^Default$/).click()
    cy.get("[role=row]").contains(/^Category1$/).click()
    cy.matchQuery("?GROUP=base&CATEGORY=Default&CATEGORY=Category1")
    cy.get("[role=row]").contains(/^Default$/).click()
    cy.matchQuery("?GROUP=base&CATEGORY=Category1")
  })

  it("should apply group filter by cell click", () => {
    cy.contains(/^group$/i).should("be.visible")
    cy.get("[role=row]").should("have.length.above", 1)
    cy.get("[role=columnheader]").contains(/^Group$/).click()
    cy.get("[role=row]").contains(/^base$/).click()
    cy.matchQuery("?GROUP=base")
  })

  it("should display usages", () => {
    cy.contains(/^Show used only$/).click()
    cy.get("[role=row]").find("a").filter((i, e) => /^\d+$/.test(e.innerText)).as("links").should("have.length", 2)
    cy.get("@links").first().click()
    cy.contains("5 more").click()
    cy.get("#app-container").toMatchImageSnapshot()
  })

  function filterByDefaultCategory() {
    // we filter by Default category to make sure that snapshots won't be made on our sandbox components li
    cy.contains(/^category$/i).should("be.visible")
    cy.get("[role=row]").should("have.length.above", 1)
    cy.get("[role=row]").contains(/^Default$/).click()
  }

  function filterByBaseGroup() {
    cy.contains(/^group$/i).parent().as("select")
    cy.get("[role=row]").should("have.length.greaterThan", 11)
    cy.get("@select").click()
    cy.get("[role=option]").as("options")
    cy.get("@options").should("have.lengthOf", totalGroups)
    cy.get("@options").contains(/^base/i).click()
    cy.get("[role=row]").should("have.lengthOf", baseGroupComponents + 1)
    cy.get("body").click()
  }

})
