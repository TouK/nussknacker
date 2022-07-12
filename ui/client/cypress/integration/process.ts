import {jsonToBlob} from "../support/tools"

describe("Process", () => {
  const seed = "process"
  const screenshotConfig = {
    blackout: [
      ".graphPage > :not(#nk-graph-main) > div",
    ],
  }

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  describe("initially clean", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed).as("processName")
    })

    it("should allow rename", () => {
      cy.intercept("PUT", "/api/processes/*").as("save")

      cy.contains(/^properties/i).should("be.enabled").click()
      cy.get("[data-testid=window]").should("be.visible").find("input").first().click().type("-renamed")
      cy.contains(/^apply/i).should("be.enabled").click()

      cy.contains(/^save/i).should("be.enabled").click()
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait("@save").its("response.statusCode").should("eq", 200)
      cy.contains(/^ok$/i).should("not.exist")
      cy.contains(/was saved$/i).should("be.visible")
      cy.contains(/scenario name changed/i).should("be.visible")
      cy.location("href").should("contain", "-renamed")
    })

    it("should allow archive with redirect to list", function () {
      cy.contains(/^archive/i).should("be.enabled").click()
      cy.contains("want to archive").should("be.visible")
      cy.contains(/^yes$/i).should("be.enabled").click()
      cy.contains(/^archived visible$/i, {timeout: 60000}).should("be.visible")
      cy.contains(this.processName).should("be.visible").click({force: true})
      cy.contains(/scenario was archived/i).should("be.visible")
    })

    it("should open properites from tips panel", () => {
      cy.viewport("macbook-15")
      cy.contains(/^properties/i).should("be.enabled").click()
      cy.get("[data-testid=window]").should("be.visible").find("input").within(inputs => {
        cy.wrap(inputs).first().click().type("-renamed")
        //this is idx of "Max events", which should be int
        cy.wrap(inputs).eq(6).click().type("wrong data")
      })
      cy.contains(/^apply/i).should("be.enabled").click()
      cy.get("[data-testid=window]").should("not.exist")
      cy.contains(/^tips.*errors in/i).contains(/^properties/i).should("be.visible").click()
      cy.get("[data-testid=window]").toMatchImageSnapshot()
    })

    it("should import JSON and save", () => {
      cy.intercept("PUT", "/api/processes/*").as("save")
      cy.contains(/is not deployed/i).should("be.visible")
      cy.get("#nk-graph-main").toMatchImageSnapshot()

      cy.intercept("POST", "/api/processes/import/*").as("import")
      cy.fixture("testProcess").then(json => {
        cy.get("@processName").then(name => cy.get("[title=import]")
          .next("[type=file]")
          .should("exist")
          .attachFile({
            fileName: "process",
            encoding: "utf-8",
            fileContent: jsonToBlob({...json, metaData: {...json.metaData, id: name}}),
          }))
      })
      cy.wait("@import").its("response.statusCode").should("eq", 200)

      cy.contains(/^save/i).should("be.enabled").click()
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait("@save").its("response.statusCode").should("eq", 200)
      cy.contains(/^ok$/i).should("not.exist")
      cy.contains(/was saved$/i).should("be.visible")
      cy.get("#nk-graph-main").wait(200).toMatchImageSnapshot()
    })
  })

  describe("with data", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed, "testProcess")
    })

    it("should allow drag node", () => {
      cy.contains(/^layout$/).click()
      cy.get("[title='toggle left panel']").click()
      cy.get("[model-id=dynamicService]")
        .should("be.visible")
        .trigger("mousedown")
        .trigger("mousemove", {clientX: 100, clientY: 100})
        .trigger("mouseup", {force: true})
      cy.get("#nk-graph-main").toMatchImageSnapshot({screenshotConfig})
    })

    it("should allow drag component and drop on edge", () => {
      cy.contains(/^layout$/).click()
      cy.contains(/^custom$/)
        .should("be.visible").click()
      cy.get("[data-testid='component:customFilter']")
        .should("be.visible")
        .drag("#nk-graph-main", {x: 580, y: 450, position: "right", force: true})
      cy.get("#nk-graph-main").toMatchImageSnapshot({screenshotConfig})
      cy.contains(/^save$/i).click()
      cy.get("[data-testid=window]").contains(/^ok$/i).click()
      cy.get("[data-testid=window]").should("not.exist")
      cy.get("#nk-graph-main").should("be.visible")
      cy.wait(100)
      cy.get("#nk-graph-main").toMatchImageSnapshot({screenshotConfig})
    })

    it("should have counts button and modal", () => {
      cy.contains(/^counts$/i).as("button")
      cy.get("@button").should("be.visible").toMatchImageSnapshot()
      cy.get("@button").click()
      cy.get("[data-testid=window]").contains("Quick ranges").should("be.visible")
      cy.get("[data-testid=window]").toMatchImageSnapshot()
    })

    it("should return 400 status code and show info about required comment", () => {
      cy.viewport("macbook-15")
      cy.contains(/^deploy$/i).click()
      cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy")
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait("@deploy", {timeout: 20000}).its("response.statusCode").should("eq", 400)
      cy.contains(/^Comment is required.$/i).should("exist")
    })

    it("should not have \"latest deploy\" button by default", () => {
      cy.viewport("macbook-15")
      cy.contains(/^deploy$/i).click()
      cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy")
      cy.get("[data-testid=window] textarea").click().type("issues/123")
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait(["@deploy", "@fetch"], {timeout: 20000}).each(res => {
        cy.wrap(res).its("response.statusCode").should("eq", 200)
      })
      cy.contains(/^counts$/i).click()
      cy.contains(/^latest deploy$/i).should("not.exist")
      cy.get("[data-testid=window]").contains("Quick ranges").should("be.visible")
      cy.get("[data-testid=window]").toMatchImageSnapshot()
      cy.get("[data-testid=window]").contains(/^cancel$/i).click()
      cy.contains(/^cancel$/i).click()
      cy.contains(/^ok$/i).should("be.enabled").click()
    })

    //Feature flag setting problem in CI
    it.skip("should have \"latest deploy\" button", () => {
      window.localStorage.setItem("persist:ff", `{"showDeploymentsInCounts": "true"}`)
      cy.reload()
      cy.viewport("macbook-15")
      cy.contains(/^deploy$/i).click()
      cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy")
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait(["@deploy", "@fetch"], {timeout: 20000}).each(res => {
        cy.wrap(res).its("response.statusCode").should("eq", 200)
      })
      cy.contains(/^counts$/i).click()
      cy.contains(/^latest deploy$/i).should("exist")
      cy.get("[data-testid=window]").should("be.visible").toMatchImageSnapshot()
      cy.get("[data-testid=window]").contains(/^cancel$/i).click()
      cy.contains(/^cancel$/i).click()
      cy.contains(/^ok$/i).should("be.enabled").click()
    })

    it("should display some node details in modal", () => {
      cy.get("[model-id=dynamicService]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=window]").contains("dynamicService").should("be.visible")
      cy.get("[data-testid=window]").should("be.visible").toMatchImageSnapshot()
      cy.get("[data-testid=window]").contains(/^cancel$/i).click()
      cy.get("[model-id=boundedSource]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=window]").contains("boundedSource").should("be.visible")
      cy.get("[data-testid=window]").should("be.visible").toMatchImageSnapshot()
      cy.get("[data-testid=window]").contains(/^cancel$/i).click()
      cy.get("[model-id=sendSms]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=window]").contains("sendSms").should("be.visible")
      cy.get("[data-testid=window]").should("be.visible").toMatchImageSnapshot()
    })
  })

  it("should preserve condition on link move (switch)", () => {
    cy.intercept("POST", "/api/*Validation", (req) => {
      if (req.body.edges.length == 3) {
        req.alias = "validation"
      }
    })
    cy.visitNewProcess(seed, "switch")
    cy.viewport(1500, 800)

    cy.getNode("switch")
      .click()
      .parent()
      .toMatchImageSnapshot({screenshotConfig: {padding: 16}})

    cy.contains(/^sinks$/)
      .should("be.visible").click()
    const x = 900
    const y = 630
    cy.get("[data-testid='component:dead-end']")
      .should("be.visible")
      .drag("#nk-graph-main", {x, y, position: "right", force: true})

    cy.get(`[model-id$="false"] [end="target"].marker-arrowhead`)
      .trigger("mousedown")
    cy.get("#nk-graph-main")
      .trigger("mousemove", {clientX: x, clientY: y})
      .trigger("mouseup", {force: true})

    cy.wait("@validation")
    cy.wait(500)

    cy.getNode("switch")
      .click()
      .parent()
      .toMatchImageSnapshot({screenshotConfig: {padding: 16}})

    cy.get(`[model-id$="false"] .label`).dblclick()
    cy.get("[data-testid=window]").should("be.visible")
    cy.contains(/^Conditions:$/).parent().toMatchImageSnapshot({screenshotConfig: {padding: 8}})
  })

  it("should preserve condition on link move (filter)", () => {
    cy.intercept("POST", "/api/*Validation", (req) => {
      if (req.body.edges.length == 2) {
        req.alias = "validation"
      }
    })
    cy.visitNewProcess(seed, "filter")
    cy.viewport(1500, 800)

    cy.get(`[model-id="dead-end(true)"]`).click().type("{backspace}")
    cy.getNode("filter")
      .click()
      .parent()
      .toMatchImageSnapshot({screenshotConfig: {padding: 16}})

    cy.contains(/^sinks$/)
      .should("be.visible").click()
    const x = 700
    const y = 600
    cy.get("[data-testid='component:dead-end']")
      .should("be.visible")
      .drag("#nk-graph-main", {x, y, position: "right", force: true})

    cy.get(`[model-id$="false"] [end="target"].marker-arrowhead`)
      .trigger("mousedown")
    cy.get("#nk-graph-main")
      .trigger("mousemove", {clientX: x, clientY: y})
      .trigger("mouseup", {force: true})

    cy.wait("@validation")
    cy.wait(500)

    cy.getNode("filter")
      .click()
      .parent()
      .toMatchImageSnapshot({screenshotConfig: {padding: 16}})
  })
})
