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
    cy.get("#graphContainer").toMatchImageSnapshot()
  })

  after(() => {
    deleteTestProcess(processName)
  })

  it("should show process diagram", () => {
    cy.url().should("contain", `visualization\/${processName}`)
    cy.contains(/has never been deployed/i).should("be.visible")
  })

  it("should import JSON and save", () => {
    cy.intercept("PUT", "/api/processes/*").as("save")
    cy.fixture("testProcess").then(json => cy.get("[title=import]")
      .next("[type=file]")
      .should("exist")
      .attachFile({fileContent: jsonToBlob({...json, metaData: {...json.metaData, id: processName}})}))
    cy.get("#graphContainer").toMatchImageSnapshot()
    cy.contains(/^save/i).should("be.enabled").click()
    cy.contains(/^ok$/i).should("be.enabled").click()
    cy.wait("@save").its("response.statusCode").should("eq", 200)
  })

  it("should allow drag model", () => {
    cy.get("[model-id=meetingService]")
      .should("be.visible")
      .trigger("mousedown", {which: 1})
      .trigger("mousemove", {clientX: 10, clientY: 10})
      .trigger("mouseup", {force: true})
    cy.get("[model-id=meetingService]").should("not.be.visible")
    cy.get("#graphContainer").toMatchImageSnapshot()
  })
})
