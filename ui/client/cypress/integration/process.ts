import {jsonToBlob} from "../support/tools"

const seed = "process"

describe("Process", () => {
  before(() => {
    cy.deleteAllTestProcesses(seed)
  })

  beforeEach(() => {
    cy.createTestProcess(seed).as("processName")
  })

  after(() => {
    cy.deleteAllTestProcesses(seed)
  })

  it("should import JSON and save", function() {
    cy.visit(`/visualization/${this.processName}?businessView=false`)
    cy.get("#graphContainer").toMatchImageSnapshot()
    cy.intercept("PUT", "/api/processes/*").as("save")
    cy.contains(/is not deployed/i).should("be.visible")

    cy.intercept("POST", "/api/processes/import/*").as("import")
    cy.fixture("testProcess").then(json => {
      cy.get("[title=import]")
        .next("[type=file]")
        .should("exist")
        .attachFile({fileContent: jsonToBlob({...json, metaData: {...json.metaData, id: this.processName}})})
    })
    cy.wait("@import").its("response.statusCode").should("eq", 200)

    cy.get("#graphContainer").toMatchImageSnapshot()
    cy.contains(/^save/i).should("be.enabled").click()
    cy.contains(/^ok$/i).should("be.enabled").click()
    cy.wait("@save").its("response.statusCode").should("eq", 200)
  })

  describe("with data", () => {
    beforeEach(function() {
      cy.importTestProcess(this.processName)
      cy.visit(`/visualization/${this.processName}?businessView=false`)
    })

    it("should allow drag model", function() {
      cy.get("[model-id=dynamicService]")
        .should("be.visible")
        .trigger("mousedown", {which: 1})
        .trigger("mousemove", {clientX: -10, clientY: -10})
        .trigger("mouseup", {force: true})
      cy.get("[model-id=dynamicService]").should("not.be.visible")
      cy.get("#graphContainer").toMatchImageSnapshot()
    })
  })
})
