const NAME = "processGrouping"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visitNewProcess(NAME, "testProcess2", `Default`)
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
      cy.get("[model-id='filter 3']").click({shiftKey: true})
      cy.get("@graph").toMatchImageSnapshot()
      cy.get("@groupButton").should("be.disabled")
      cy.get("[model-id='filter 3']").click({shiftKey: true})
      cy.get("@groupButton").should("be.enabled").click()
      cy.get("@layoutButton").click()
      cy.get("@graph").wait(200).toMatchImageSnapshot()
    })

    it("should ungroup work for multiple", () => {
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
      cy.get("@graph").wait(200).toMatchImageSnapshot()
    })

    it("should open group details on collapsed and expanded group", () => {
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 800, 10, {metaKey: true, force: true})
        .trigger("mousemove", 500, 600, {metaKey: true, force: true})
        .trigger("mouseup", {force: true})
      cy.get("@groupButton").should("be.enabled").click()

      cy.get("[model-id*=source-filter].joint-type-esp-group").dblclick()
      cy.get("[data-testid=node-modal]").should("be.visible")
        .contains(/^expand/i).should("be.enabled").click()

      cy.get("[model-id*=source-filter].joint-type-basic-rect").dblclick("top")
      cy.get("[data-testid=node-modal]").should("be.visible")
        .contains(/^collapse/i).should("be.enabled").click()
    })
  })
})
