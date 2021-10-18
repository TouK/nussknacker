describe("Fragment", {
  viewportHeight: 1000,
  viewportWidth: 1440,
}, () => {
  const seed = "fragment"
  const screenshotConfig = {
    blackout: [
      ".graphPage > :not(#nk-graph-main) > div",
    ],
  }

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  it("should allow adding input parameters and display used fragment graph in modal", () => {
    cy.visitNewFragment(seed, "fragment").as("fragmentName")
    cy.get("[model-id=input]").should("be.visible").trigger("dblclick")
    cy.get("[data-testid=window]").should("be.visible").as("window")
    cy.get("@window").contains("+").click()
    cy.get("[data-testid='fieldsRow:3']").find(".fieldName input").type("xxxx")
    cy.get("[data-testid='draggable:3'] [role='button']").dndTo("[data-testid='draggable:0']")
    cy.get("[data-testid='fieldsRow:0']").find(".fieldName input").should("have.value", "xxxx")
    cy.get("@window").toMatchImageSnapshot()
    cy.get("@window").contains(/^apply$/i).click()
    cy.contains(/^save$/i).click()
    cy.contains(/^ok$/i).click()

    cy.visitNewProcess(seed, "testProcess")
    cy.contains(/^layout$/i).click()

    cy.contains("fragments").should("be.visible").click()
    cy.contains("fragment-test")
      .last()
      .should("be.visible")
      .drag("#nk-graph-main", {x: 800, y: 600, position: "right", force: true})
    cy.contains(/^layout$/i).click()

    cy.get("[model-id$=-fragment-test-process]").should("be.visible").trigger("dblclick")
    cy.get("#nk-graph-subprocess [model-id='input']").should("be.visible")
    cy.wait(500)
    cy.get("[data-testid=window]").toMatchImageSnapshot()
    cy.get("button[name='close']").click()

    cy.get("#nk-graph-main").toMatchImageSnapshot({screenshotConfig})

    cy.get("[model-id$=sendSms]").should("be.visible").trigger("dblclick")
    cy.get(".ace_editor").should("be.visible").type("{selectall}#testOutput.")
    cy.get("[data-testid=window]").toMatchImageSnapshot()
  })

  it("should open properties", () => {
    cy.visitNewFragment(seed, "fragment").as("fragmentName")
    cy.contains(/^properties/i).should("be.enabled").click()
    cy.contains(/^apply/i).should("be.enabled")
    cy.get("[data-testid=window]").toMatchImageSnapshot()
  })
})
