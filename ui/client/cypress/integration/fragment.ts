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

    cy.contains(/^fragments$/).should("be.visible").click()
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

  it("should add documentation url in fragment properties and show it in modal within scenario", () => {
    const seed2 = "fragment2"
    cy.visitNewFragment(seed2, "fragment").as("fragmentName")
    cy.contains(/^properties/i).should("be.enabled").click()

    const docsUrl = "https://nussknacker.io/"

    cy.get("[data-testid=window]").should("be.visible").find("input").within(inputs => {
      cy.wrap(inputs).eq(1).click().type(docsUrl)
    })

    cy.contains(/^apply/i).should("be.enabled").click()
    cy.contains(/^save/i).should("be.enabled").click()
    cy.intercept("PUT", "/api/processes/*").as("save")
    cy.contains(/^ok$/i).should("be.enabled").click()

    cy.wait(["@save", "@fetch"], {timeout: 20000}).each(res => {
      cy.wrap(res).its("response.statusCode").should("eq", 200)
    })
    cy.contains(/^ok$/i).should("not.exist")
    cy.contains(/was saved$/i).should("be.visible")

    cy.visitNewProcess(seed, "testProcess")
    cy.contains(/^layout$/i).click()

    cy.contains("fragments").should("be.visible").click()
    cy.contains(`${seed2}-test`)
      .last()
      .should("be.visible")
      .drag("#nk-graph-main", {x: 800, y: 600, position: "right", force: true})
    cy.contains(/^layout$/i).click()

    cy.get(`[model-id$=-${seed2}-test-process]`).should("be.visible").trigger("dblclick")

    cy.get("[title='Documentation']").should("have.attr", "href", docsUrl)
    cy.get("[data-testid=window]").as("window")
    cy.get("@window").contains(/^input$/).should("be.visible")
    cy.get("@window").wait(200).toMatchImageSnapshot()

    cy.deleteAllTestProcesses({filter: seed2})
  })

})
