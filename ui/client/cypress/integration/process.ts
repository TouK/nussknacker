import {jsonToBlob} from "../support/tools"

const seed = "process"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses(seed)
  })

  after(() => {
    cy.deleteAllTestProcesses(seed)
  })

  describe("initially clean", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed).as("processName")
    })

    it("should import JSON and save", () => {
      cy.intercept("PUT", "/api/processes/*").as("save")
      cy.contains(/is not deployed/i).should("be.visible")
      cy.get("#graphContainer").toMatchImageSnapshot()

      cy.intercept("POST", "/api/processes/import/*").as("import")
      cy.fixture("testProcess").then(json => {
        cy.get("@processName").then(id => cy.get("[title=import]")
          .next("[type=file]")
          .should("exist")
          .attachFile({fileContent: jsonToBlob({...json, metaData: {...json.metaData, id}})}))
      })
      cy.wait("@import").its("response.statusCode").should("eq", 200)

      cy.contains(/^save/i).should("be.enabled").click()
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait("@save").its("response.statusCode").should("eq", 200)
      cy.contains(/^ok$/i).should("not.exist")
      cy.contains(/was saved$/i).should("be.visible")
      cy.get("#graphContainer").toMatchImageSnapshot()
    })
  })

  describe("with data", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed, "testProcess")
    })

    it("should allow drag model", () => {
      cy.get("[title='toggle left panel']").click()
      cy.get("[title='toggle right panel']").click()
      cy.get("[model-id=dynamicService]")
        .should("be.visible")
        .trigger("mousedown", {which: 1})
        .trigger("mousemove", {clientX: 100, clientY: 100})
        .trigger("mouseup", {force: true})
      cy.get("#graphContainer").toMatchImageSnapshot()
    })

    it("should have counts button and modal", () => {
      cy.contains(/^counts$/i).as("button")
      cy.get("@button").should("be.visible").toMatchImageSnapshot()
      cy.get("@button").click()
      cy.get("[data-testid=modal]").should("be.visible").toMatchImageSnapshot()
    })

    it("should not have \"latest deploy\" button by default", () => {
      cy.viewport("macbook-15")
      cy.contains(/^deploy$/i).click()
      cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy")
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait(["@deploy", "@fetch"], {timeout: 20000}).each(res => {
        cy.wrap(res).its("response.statusCode").should("eq", 200)
      })
      cy.contains(/^counts$/i).click()
      cy.contains(/^latest deploy$/i).should("not.exist")
      cy.get("[data-testid=modal]").should("be.visible").toMatchImageSnapshot()
    })

    it("should have \"latest deploy\" button", () => {
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
      cy.get("[data-testid=modal]").should("be.visible").toMatchImageSnapshot()
    })

    it("should display some node details in modal", () => {
      cy.get("[model-id=dynamicService]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=node-modal]").should("be.visible").toMatchImageSnapshot()
      cy.get("[data-testid=node-modal]").contains(/^cancel$/i).click()
      cy.get("[model-id=boundedSource]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=node-modal]").should("be.visible").toMatchImageSnapshot()
      cy.get("[data-testid=node-modal]").contains(/^cancel$/i).click()
      cy.get("[model-id=sendSms]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=node-modal]").should("be.visible").toMatchImageSnapshot()
    })
  })

  describe("with sql-editor", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed, "withSqlEditor")
    })

    it("should display colorfull sql editor", () => {
      cy.get("[model-id=sql-source]").should("be.visible").trigger("dblclick")
      cy.get("[data-testid=node-modal]").should("be.visible").toMatchImageSnapshot()
      cy.get("[title='Switch to basic mode']").trigger("click")
      cy.get("[data-testid=node-modal]").toMatchImageSnapshot()
    })
  })
})
