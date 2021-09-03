const NAME = "processSelection"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visitNewProcess(NAME, "testProcess")
    cy.contains("layout").click()
    cy.get("#nk-graph-main svg", {timeout: 20000}).as("graph")
  })

  describe("mouse drag", () => {
    beforeEach(() => {
      cy.get("[title='toggle left panel']").click()
      //Currently with default settings right toggle is not enabled, so we'll stick with unnecessary items on snapshot
      //cy.get("[title='toggle right panel']").click()
    })

    it("should allow pan view", () => {
      cy.get("@graph")
        .trigger("mousedown", 10, 10, {force: true})
        .trigger("mousemove", 200, 100, {force: true})
        .trigger("mouseup", {force: true})
        .wait(200)
        .toMatchExactImageSnapshot()
    })

    it("should select only fully covered (to right)", () => {
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 300, 100, {metaKey: true, force: true})
        .trigger("mousemove", 700, 500, {metaKey: true, force: true})
        .toMatchExactImageSnapshot()
      cy.get("@graph")
        .trigger("mouseup", {force: true})
        .toMatchExactImageSnapshot()
    })

    it("should select partially covered (to left)", () => {
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 700, 100, {metaKey: true, force: true})
        .trigger("mousemove", 500, 500, {metaKey: true, force: true})
        .toMatchExactImageSnapshot()
      cy.get("@graph")
        .trigger("mouseup", {force: true})
        .toMatchExactImageSnapshot()
    })

    it("should switch modes, append and inverse select with shift", () => {
      cy.get("@graph")
        .trigger("keydown", {key: "Meta"})
        .trigger("mousedown", 700, 100, {metaKey: true, force: true})
        .trigger("mousemove", 500, 400, {metaKey: true, force: true})
        .toMatchExactImageSnapshot()
      cy.get("@graph")
        .trigger("mouseup", {force: true})
        .trigger("keyup", {key: "Meta"})
        .toMatchExactImageSnapshot()
      cy.get("@graph")
        .trigger("keydown", {key: "Shift"})
        .trigger("mousedown", 700, 150, {shiftKey: true, force: true})
        .trigger("mousemove", 500, 550, {shiftKey: true, force: true})
        .toMatchExactImageSnapshot()
      cy.get("@graph")
        .trigger("mouseup", {force: true})
        .toMatchExactImageSnapshot()
    })
  })
})
