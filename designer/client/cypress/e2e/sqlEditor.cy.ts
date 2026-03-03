describe("Sql editor", () => {
    const seed = "sql";
    const maxDiffThreshold = 0.0001;

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display colorfull sql code", () => {
        cy.visitNewProcess(seed, "withSqlEditor");
        cy.getNode("sql-source").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").should("be.visible");
        cy.get("#ace-editor").should("have.class", "tokenizer-working");
        cy.get("#ace-editor").should("not.have.class", "tokenizer-working");
        cy.get("#ace-editor").parent().matchImage({ maxDiffThreshold });
        cy.get("[data-testid=window]").matchImage();
    });

    it("should display advanced colors", () => {
        cy.viewport("macbook-15");
        cy.visitNewProcess(seed, "withSqlEditor2");

        cy.wrap(["sql-source", "sql-source2", "sql-source3"]).each((name: string) => {
            cy.getNode(name).should("be.visible").trigger("dblclick");
            cy.get("#ace-editor").should("have.class", "tokenizer-working");
            cy.get("#ace-editor").should("not.have.class", "tokenizer-working");
            cy.get("#ace-editor").parent().matchImage({ maxDiffThreshold });
            cy.get("[data-testid=window]")
                .contains(/^cancel$/i)
                .click();
        });
    });

    it("should insert '#{ # }' snippet when '#{' provided", () => {
        cy.visitNewProcess(seed, "withSqlEditor");
        cy.getNode("sql-source").should("be.visible").trigger("dblclick");
        cy.get("[title='sql']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        cy.get("@editor").click();
        cy.realPress([Cypress.platform === "darwin" ? "Meta" : "Control", "a"]); // Select all (Cmd+A)
        cy.realPress("Backspace"); // Delete selection
        cy.realType("#{");

        cy.get("@editor").within(() => {
            cy.window().then((win) => {
                const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[0]);
                expect(aceEditor.getValue()).to.eq("#{ # }");
            });
        });
    });
});
