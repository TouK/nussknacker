const seed = "sql"

describe("Sql editor", () => {
  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  it("should display colorfull sql code", () => {
    cy.visitNewProcess(seed, "withSqlEditor")
    cy.get("[model-id=sql-source]").should("be.visible").trigger("dblclick")
    cy.get("[data-testid=node-modal]").should("be.visible")
    cy.get("#ace-editor").should("not.have.class", "tokenizer-working").parent().toMatchExactImageSnapshot()
    cy.get("[title='Switch to basic mode']").trigger("click")
    cy.get("[data-testid=node-modal]").toMatchImageSnapshot()
  })

  it("should display advanced colors", () => {
    cy.viewport("macbook-15")
    cy.visitNewProcess(seed, "withSqlEditor2")

    cy.wrap(["sql-source", "sql-source2", "sql-source3"]).each(name => {
      cy.get(`[model-id=${name}]`).should("be.visible").trigger("dblclick")
      cy.get("#ace-editor").should("not.have.class", "tokenizer-working").parent()
        .toMatchExactImageSnapshot()
      cy.get("[data-testid=node-modal]").contains(/^cancel$/i).click()
    })
  })
})
