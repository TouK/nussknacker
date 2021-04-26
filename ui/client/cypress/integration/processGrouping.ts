const NAME = "processNodes"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visitNewProcess(NAME, "testProcess2")
    cy.get("#esp-graph svg", {timeout: 20000}).as("graph")
  })

  describe("grouping", () => {
    beforeEach(() => {
      cy.get("[title='toggle left panel']").click()
      cy.get("button[title=layout]").should("exist").as("layoutButton")
      cy.get("button").contains(/^group$/i).parent().should("exist").as("groupButton")
      cy.get("button").contains(/^ungroup$/i).parent().should("exist").as("ungroupButton")
    })

    it("should only work for continous selection", () => {
      cy.get("@groupButton").should("be.disabled")
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 300, 100, {metaKey: true, force: true})
        .trigger("mousemove", 680, 550, {metaKey: true, force: true})
        .trigger("mouseup", {force: true})
      cy.get("[model-id=aggregate]").click({shiftKey: true})
      cy.get("@graph").toMatchImageSnapshot()
      cy.get("@groupButton").should("be.disabled")
      cy.get("[model-id=aggregate]").click({shiftKey: true})
      cy.get("@groupButton").should("be.enabled").click()
      cy.get("@layoutButton").click()
      cy.get("@graph").toMatchImageSnapshot()
    })
    it("should ungrout work for multiple", () => {
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 800, 10, {metaKey: true, force: true})
        .trigger("mousemove", 500, 150, {metaKey: true, force: true})
        .trigger("mouseup", {force: true})
      cy.get("@groupButton").should("be.enabled").click()
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 800, 150, {metaKey: true, force: true})
        .trigger("mousemove", 500, 300, {metaKey: true, force: true})
        .trigger("mouseup", {force: true})
      cy.get("@groupButton").should("be.enabled").click()
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 800, 300, {metaKey: true, force: true})
        .trigger("mousemove", 500, 500, {metaKey: true, force: true})
        .trigger("mouseup", {force: true})
      cy.get("@groupButton").should("be.enabled").click()
      cy.get("@layoutButton").click()
      cy.get("@graph").toMatchImageSnapshot()
      cy.get("@ungroupButton").should("be.disabled")
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 800, 10, {metaKey: true, force: true})
        .trigger("mousemove", 500, 600, {metaKey: true, force: true})
        .trigger("mouseup", {force: true})
      cy.get("@ungroupButton").should("be.enabled").click()
      cy.get("@layoutButton").click()
      cy.get("@graph").toMatchImageSnapshot()
    })
  })
})
