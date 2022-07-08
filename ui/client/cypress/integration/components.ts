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
    cy.contains(/^for-each$/).should("be.visible")
    cy.get("#app-container").toMatchImageSnapshot()
  })

  it("should have dynamic page size", () => {
    filterByDefaultCategory()
    cy.contains(/^1–13 of \d+$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 14)
    cy.viewport(1400, 500)
    cy.contains(/^1–3 of \d+$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 4)
  })

  it("should allow filtering by name", () => {
    filterByDefaultCategory()
    cy.get("[placeholder='Search...']").type("for", {force: true})
    cy.contains(/^for-each$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 2)
    cy.get("[placeholder='Search...']").type("-dummy")
    cy.get("[role=row]").should("have.lengthOf", 1)
    cy.matchQuery("?CATEGORY=Default&NAME=for-dummy")
  })

  it("should allow filtering by name with multiple words", () => {
    filterByDefaultCategory()
    cy.get("[placeholder='Search...']").type("fo ea", {force: true, delay: 100})
    cy.contains(/^for-each$/i).should("be.visible")
    cy.get("[role=row]").should("have.lengthOf", 2)
    cy.matchQuery("?CATEGORY=Default&NAME=fo+ea")
    cy.get("[role=grid]").toMatchImageSnapshot()
  })

  it("should allow filtering by group", () => {
    filterByDefaultCategory()
    cy.get("[role=row]").should("have.length.greaterThan", 11)
    cy.contains(/^group$/i).click()
    cy.get("[role=menuitem]").as("options")
    cy.get("@options").should("have.lengthOf", totalGroups + 1)
    cy.get("@options").contains(/^base/i).click()
    cy.matchQuery("?CATEGORY=Default&GROUP=base")
    cy.get("@options").contains(/^source/i).click()
    cy.matchQuery("?CATEGORY=Default&GROUP=base&GROUP=sources")
    cy.get("[role=row]").should("have.lengthOf", 11)
    cy.get("body").click()
    cy.contains(`:has([data-testid="CancelIcon"])`, /^sources/).find(`[data-testid="CancelIcon"]`).click()
    cy.get("[role=row]").should("have.lengthOf", baseGroupComponents + 1).contains(":not(title)", /^filter$/).should("be.visible")
    cy.get(`[data-testid="FilterListOffIcon"]`).click()
    cy.get("[role=row]").should("have.length.greaterThan", 2)
  })

  it("should allow filtering by usage", () => {
    filterByDefaultCategory()
    cy.contains(/^usages$/i).click()
    cy.contains(/^≥ 1$/i).click()
    cy.matchQuery("?CATEGORY=Default&USAGES=1")
    cy.get("[role=row]").should("have.lengthOf", 3)
    cy.get("#app-container>main").toMatchImageSnapshot()
    cy.contains(/^< 1$/i).click()
    cy.matchQuery("?CATEGORY=Default&USAGES=-1")
    cy.get("[role=row]").should("have.length.above", 3)
    cy.get("#app-container>main").toMatchImageSnapshot()
  })

  it("should display component usage with working scenario link", () => {
    cy.contains(/^usages$/i).click()
    cy.contains(/^≥ 1$/i).click()
    cy.get("body").click()
    cy.get("[data-id=filter] [data-testid=LinkIcon]").click()
    cy.contains("components-test").click()
    cy.contains("import test data").should("be.visible")
  })

  it("should apply filters from query", () => {
    cy.visit("/customtabs/components?NAME=split&GROUP=base&CATEGORY=Default&CATEGORY=DemoFeatures&USAGES=-1")
    cy.contains(/^name$/i).should("be.visible")
    cy.get("[role=row]").should("have.length", 2)
    cy.contains("[role=row] *", /^Default$/).should("be.visible")
    cy.wait(300)
    cy.get("#app-container>main").toMatchImageSnapshot()
  })

  it("should apply category filters by cell click", () => {
    filterByBaseGroup()
    cy.contains(/^category$/i).should("be.visible")
    cy.get("[role=row]").should("have.length.above", 1)
    cy.contains("[role=row] *", /^Default$/).click()
    cy.contains("[role=row] *", /^Category1$/).click()
    cy.matchQuery("?GROUP=base&CATEGORY=Default&CATEGORY=Category1")
    cy.contains("[role=row] *", /^Default$/).click()
    cy.matchQuery("?GROUP=base&CATEGORY=Category1")
  })

  it("should apply group filter by cell click", () => {
    cy.contains(/^group$/i).should("be.visible")
    cy.get("[role=row]").should("have.length.above", 1)
    cy.contains("[role=columnheader] *", /^Group$/).click()
    cy.contains("[role=row] *", /^base$/).click()
    cy.matchQuery("?GROUP=base")
  })

  it("should display usages", () => {
    cy.contains(/^usages$/i).click()
    cy.contains(/^≥ 1$/i).click()
    cy.get("body").click()

    cy.get("[role=row] a")
      // this number is two times larger than number of components with some usages because it handle also links to documentation
      .should("have.length", 4)
      .as("links")

    // we are clicking filter component because it has many usages and we are able to test usages list expansion
    cy.get("@links")
      .filter((i, e) => /^\d+$/.test(e.innerText))
      .its(1)
      .click()

    // we are clicking "X more" on list of places of usages to test usages list expansion
    cy.contains("4 more").click()
    cy.get("#app-container>main").toMatchImageSnapshot({
      screenshotConfig: {clip: {x: 0, y: 0, width: 1400, height: 300}},
    })
  })

  it("should filter usages", () => {
    cy.createTestProcess(`${seed}_xxx`, "testProcess2")

    cy.visit("/customtabs/components/usages/filter")

    cy.get("input[type=text]").type("8 xxx")
    cy.contains(/^filter 8$/).should("be.visible")

    cy.wait(500) //ensure "loading" mask is hidden
    cy.get("#app-container>main").toMatchImageSnapshot({
      screenshotConfig: {clip: {x: 0, y: 0, width: 1400, height: 300}},
    })
  })

  function filterByDefaultCategory() {
    // we filter by Default category to make sure that snapshots won't be made on our sandbox components li
    cy.contains(/^category$/i, {timeout: 60000}).should("be.visible")
    cy.get("[role=row]").should("have.length.above", 2)
    cy.contains("[role=row] *", /^Default$/).click()
  }

  function filterByBaseGroup() {
    cy.get("[role=row]", {timeout: 60000}).should("have.length.greaterThan", 11)
    cy.contains(/^group$/i).click()
    cy.get("[role=menuitem]").as("options")
    cy.get("@options").should("have.lengthOf", totalGroups + 1)
    cy.get("@options").contains(/^base/i).click()
    cy.get("[role=row]").should("have.lengthOf", baseGroupComponents + 1)
    cy.get("body").click()
  }

})
