describe.only("Undo/Redo", () => {
  const seed = "undo"
  const screenshotOptions: Cypress.MatchImageOptions = {
    maxDiffThreshold: 0.005,
    screenshotConfig: {
      blackout: [
        "> :not(#nk-graph-main) > div",
      ],
    },
  }

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  beforeEach(() => {
    cy.visitNewProcess(seed, "testProcess")
    cy.contains(/^custom$/).should("be.visible").click()
    cy.layoutScenario()
    cy.contains(/^undo$/i).as("undo").should("be.disabled")
    cy.contains(/^redo$/i).as("redo").should("be.disabled")
  })

  it("should work for add and move to edge", () => {
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("[data-testid='component:customFilter']")
      .should("be.visible")
      .drag("#nk-graph-main", {x: 480, y: 450, position: "right", force: true})
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("[model-id=customFilter]")
      .should("be.visible")
      .trigger("mousedown")
      .trigger("mousemove", {clientX: 560, clientY: 500})
      .click()
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("@undo")
      .should("be.enabled")
      .click()
      .click()
      .click()
      .click()
      .click()
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("@undo")
      .should("be.enabled")
      .click()
      .should("be.disabled")
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("@redo")
      .should("be.enabled")
      .click()
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("@redo")
      .should("be.enabled")
      .click()
      .click()
      .click()
      .click()
      .click()
      .should("be.disabled")
    cy.get(".graphPage").matchImage(screenshotOptions)
  })

  it("should work for drop on edge", () => {
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("[data-testid='component:customFilter']")
      .should("be.visible")
      .drag("#nk-graph-main", {x: 580, y: 450, position: "right", force: true})
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("@undo")
      .should("be.enabled")
      .click()
      .click()
      .click()
      .click()
      .click()
      .should("be.disabled")
    cy.get(".graphPage").matchImage(screenshotOptions)
    cy.get("@redo")
      .should("be.enabled")
      .click()
      .click()
      .click()
      .click()
      .click()
      .should("be.disabled")
    cy.get(".graphPage").matchImage(screenshotOptions)
  })
})
