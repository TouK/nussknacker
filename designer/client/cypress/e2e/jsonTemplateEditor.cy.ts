describe("JSON template editor", () => {
    const seed = "jsonTemplate";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should insert '#{ # }' snippet when '#{' provided", () => {
        cy.visitNewProcess(seed, "jsonTemplateEditor", "Category1");
        cy.getNode("Event Generator").should("be.visible").trigger("dblclick");
        cy.get("[title='value']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        cy.get("@editor").click();

        cy.realPress([Cypress.platform === "darwin" ? "Meta" : "Control", "a"]); // Select all (Cmd+A)
        cy.realPress("Backspace"); // Delete selection
        cy.realType("{");
        cy.realPress("Enter");
        cy.realType("key1");
        cy.realPress("Tab");
        cy.realType('"#{');

        cy.get("@editor").within(() => {
            cy.window().then((win) => {
                const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[0]);
                expect(aceEditor.getValue()).to.eq('{\n  "key1": "#{ # }"\n}');
            });
        });
    });

    it("should reset values to the default on reset button click", () => {
        cy.visitNewProcess(seed, "jsonTemplateEditor", "Category1");

        cy.toggleUserFlag("editor.showResetToDefaultButton");

        cy.getNode("Event Generator").should("be.visible").trigger("dblclick");
        cy.get("[title='value']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        cy.get("@editor").click();

        cy.realPress([Cypress.platform === "darwin" ? "Meta" : "Control", "a"]); // Select all (Cmd+A)
        cy.realPress("Backspace");

        // Apply default values
        cy.get("[data-testid=window]").find("[data-testid=resetToDefaultButton]").click();
        cy.contains("button", /apply value/i).click();

        // Verify default values
        cy.get("@editor").within(() => {
            cy.window().then((win) => {
                const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[0]);
                expect(aceEditor.getValue()).to.eq(
                    '{\n\t"sampleField": "#{ #UTIL.uuid }",\n\t"dateTime": "#{ #DATE.now }",\n\t"type": "example",\n\t"value": 100\n}',
                );
            });
        });

        cy.toggleUserFlag("editor.showResetToDefaultButton");
    });
});
