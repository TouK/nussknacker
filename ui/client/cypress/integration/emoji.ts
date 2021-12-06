describe("Emoji", () => {
  const NAME = "🎄🎅🎁"

  before(() => {
    cy.deleteAllTestProcesses({filter: NAME, force: true})
    cy.createTestProcess(NAME)
    cy.viewport("macbook-16")
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: NAME})
  })

  it("should display big emoji in list", () => {
    cy.visit("/")
    cy.contains(/^create new scenario$/i).should("be.visible").click()
    cy.get("#newProcessId").type(NAME)
    cy.contains(/^create$/i).should("be.enabled").click()
    cy.visit("/")
    cy.get("tbody tr").should("have.length", 2)
    cy.wait(500)
    cy.get("main").toMatchImageSnapshot()
  })
  it("should display big emoji in node", () => {
    cy.visitNewProcess(NAME, "emoji")
    cy.contains("🎄").should("be.visible")
    cy.contains(/^layout$/i).click()
    cy.wait(500)
    cy.get("#nk-graph-main").toMatchImageSnapshot({
      screenshotConfig: {
        blackout: [
          ".graphPage > :not(#nk-graph-main) > div",
        ],
      },
    })
  })
})
