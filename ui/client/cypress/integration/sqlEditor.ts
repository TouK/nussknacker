describe("Sql editor", () => {
  const seed = "sql"

  before(() => {
    cy.deleteAllTestProcesses({filter: seed, force: true})
  })

  after(() => {
    cy.deleteAllTestProcesses({filter: seed})
  })

  it("should display colorfull sql code", () => {
    cy.visitNewProcess(seed, "withSqlEditor")
    cy.contains("layout").click()
    cy.get("[model-id=sql-source]").should("be.visible").trigger("dblclick")
    cy.get("[data-testid=window]").should("be.visible")
    cy.get("#ace-editor").should("not.have.class", "tokenizer-working").parent().toMatchExactImageSnapshot()
    cy.get("[title='Switch to basic mode']").trigger("click")
    cy.get("[data-testid=window]").toMatchImageSnapshot()
  })

  it("should display advanced colors", () => {
    cy.viewport("macbook-15")
    cy.visitNewProcess(seed, "withSqlEditor2")
    cy.contains("layout").click()

    cy.wrap(["sql-source", "sql-source2", "sql-source3"]).each(name => {
      cy.get(`[model-id=${name}]`).should("be.visible").trigger("dblclick")
      cy.get("#ace-editor").should("not.have.class", "tokenizer-working").parent()
        .toMatchExactImageSnapshot()
      cy.get("[data-testid=window]").contains(/^cancel$/i).click()
    })
  })
})
