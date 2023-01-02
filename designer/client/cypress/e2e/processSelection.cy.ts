describe("Process mouse drag", () => {
  const NAME = "processSelection"
  const snapshotParams: Cypress.MatchImageOptions = {
    maxDiffThreshold: 0.0001,
    screenshotConfig: {
      blackout: [
        "> :not(#nk-graph-main) > div",
      ],
    },
  }

  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  beforeEach(() => {
    cy.visitNewProcess(NAME, "testProcess")
    cy.get(".graphPage", {timeout: 20000}).as("graph").within(() => {
      cy.get("#nk-graph-main svg", {timeout: 20000}).as("canvas")
    })
    cy.get("[title='toggle left panel']").click()
    cy.layoutScenario()
  })

  it("should allow pan view", () => {
    cy.get("@canvas")
      .trigger("mousedown", 10, 10, {force: true})
      .trigger("mousemove", 200, 100, {force: true})
      .trigger("mouseup", {force: true})
      .wait(200)
    cy.get("@graph").matchImage(snapshotParams)
  })

  it("should select only fully covered (to right)", () => {
    cy.get("@canvas")
      .trigger("keydown", {key: "Meta"})
      .trigger("mousedown", 300, 100, {metaKey: true, force: true})
      .trigger("mousemove", 700, 500, {metaKey: true, force: true})
    cy.get("@graph").matchImage(snapshotParams)
    cy.get("@canvas")
      .trigger("mouseup", {force: true})
    cy.get("@graph").matchImage(snapshotParams)
  })

  it("should select partially covered (to left)", () => {
    cy.get("@canvas")
      .trigger("keydown", {key: "Meta"})
      .trigger("mousedown", 700, 100, {metaKey: true, force: true})
      .trigger("mousemove", 500, 500, {metaKey: true, force: true})
    cy.get("@graph").matchImage(snapshotParams)
    cy.get("@canvas")
      .trigger("mouseup", {force: true})
    cy.get("@graph").matchImage(snapshotParams)
  })

  it("should switch modes, append and inverse select with shift", () => {
    cy.get("@canvas")
      .trigger("keydown", {key: "Meta"})
      .trigger("mousedown", 700, 100, {metaKey: true, force: true})
      .trigger("mousemove", 500, 400, {metaKey: true, force: true})
    cy.get("@graph").matchImage(snapshotParams)
    cy.get("@canvas")
      .trigger("mouseup", {force: true})
      .trigger("keyup", {key: "Meta"})
    cy.get("@graph").matchImage(snapshotParams)
    cy.get("@canvas")
      .trigger("keydown", {key: "Shift"})
      .trigger("mousedown", 700, 150, {shiftKey: true, force: true})
      .trigger("mousemove", 500, 550, {shiftKey: true, force: true})
    cy.get("@graph").matchImage(snapshotParams)
    cy.get("@canvas")
      .trigger("mouseup", {force: true})
    cy.get("@graph").matchImage(snapshotParams)
  })
})
