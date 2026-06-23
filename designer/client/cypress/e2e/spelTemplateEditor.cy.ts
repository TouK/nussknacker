describe("SpEL template editor", () => {
    const seed = "spelTemplate";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should insert '#{ # }' snippet when '#{' provided", () => {
        cy.visitNewProcess(seed, "spelTemplateEditor", "Category1");
        cy.getNode("Delay").should("be.visible").trigger("dblclick");
        cy.get("[title='keyBy']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        cy.get("@editor").click();

        cy.realType("#{");

        cy.get("@editor").within(() => {
            cy.window().then((win) => {
                const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[0]);
                expect(aceEditor.getValue()).to.eq("#{ # }");
            });
        });
    });
});
