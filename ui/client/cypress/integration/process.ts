import {createTestProcess, deleteTestProcess, getProcessName, jsonToBlob} from "../support/tools"

describe("Processes diagram", () => {
  let processName: string

  before(() => {
    processName = getProcessName()
    createTestProcess(processName)
  })

  beforeEach(() => {
    cy.fixture("env")
      .then(({BASIC_AUTH}) => cy.visit(`/visualization/${processName}?businessView=false`, {auth: BASIC_AUTH}))
  })

  after(() => {
    deleteTestProcess(processName)
  })

  it("should show process diagram", () => {
    cy.url().should("contain", `visualization\/${processName}`)
    cy.contains(/has never been deployed/i).should("be.visible")
  })

  it("should import JSON and allow drag", () => {
    cy.fixture("testProcess.json").then(json => cy.get("[title=import]")
      .next("[type=file]")
      .should("exist")
      .attachFile({fileContent: jsonToBlob({...json, metaData: {...json.metaData, id: processName}})}))
    cy.get("[model-id=meetingService]").should("be.visible")
    cy.get("[model-id=meetingService]")
      .trigger("mousedown", {which: 1})
      .trigger("mousemove", {clientX: 10, clientY: 10})
      .trigger("mouseup", {force: true})
    cy.get("[model-id=meetingService]").should("not.be.visible")
  })
})
