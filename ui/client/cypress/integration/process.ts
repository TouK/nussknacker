import {jsonToBlob} from "../support/tools"

const seed = "process"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  describe("initially clean", () => {
    beforeEach(() => {
      cy.visitNewProcess(seed).as("processName")
    })

    it("should allow rename", () => {
      cy.intercept("PUT", "/api/processes/*").as("save")

      cy.contains(/^properties/i).should("be.enabled").click()
      cy.get("[data-testid=node-modal]").should("be.visible").find("input").first().click().type("-renamed")
      cy.contains(/^apply/i).should("be.enabled").click()

      cy.contains(/^save/i).should("be.enabled").click()
      cy.contains(/^ok$/i).should("be.enabled").click()
      cy.wait("@save").its("response.statusCode").should("eq", 200)
      cy.contains(/^ok$/i).should("not.exist")
      cy.contains(/was saved$/i).should("be.visible")
      cy.contains(/process name changed/i).should("be.visible")
      cy.location("href").should("contain", "-renamed")
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
        .trigger("mousedown")
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
      cy.get("[data-testid=modal]").contains(/^cancel$/i).click()
      cy.contains(/^cancel$/i).click()
      cy.contains(/^ok$/i).should("be.enabled").click()
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
      cy.get("[data-testid=modal]").contains(/^cancel$/i).click()
      cy.contains(/^cancel$/i).click()
      cy.contains(/^ok$/i).should("be.enabled").click()
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
})
