describe("JSON editor", () => {
    const seed = "json";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display colorfull json", () => {
        cy.visitNewProcess(seed, "rr", "RequestResponse");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title='Input schema']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        cy.get("@editor").matchImage({
            maxDiffThreshold: 0.0025,
            screenshotConfig: { padding: [1] },
        });

        cy.get("@editor")
            .click("center")
            .type("{enter}false")
            .wait(300)
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [1, 1, 24, 1] },
            });
    });

    it("should verify JSON Based snippets", () => {
        cy.visitNewProcess(seed, "rr", "RequestResponse");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title='Output schema']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        // Verify enter between {} case
        cy.get("@editor").click();
        cy.realPress("ArrowLeft");
        cy.realPress("Enter");
        cy.realType("key1");
        cy.realPress("Tab");
        cy.realType('"value1",');
        // Verify next property case
        cy.realPress("Enter");
        cy.realType("key2");
        cy.realPress("Tab");
        cy.realType('"value2"');

        cy.get("@editor").within(() => {
            cy.window().then((win) => {
                const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[1]);
                expect(aceEditor.getValue()).to.eq('{\n  "key1": "value1",\n  "key2": "value2"\n}');
            });
        });

        // Verify open bracket { case
        cy.realPress([Cypress.platform === "darwin" ? "Meta" : "Control", "a"]); // Select all (Cmd+A)
        cy.realPress("Backspace"); // Delete selection
        cy.realType("{");
        cy.realPress("Enter");
        cy.realType("key1");
        cy.realPress("Tab");
        cy.realType('"value1"');
        cy.get("@editor").within(() => {
            cy.window().then((win) => {
                const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[1]);
                expect(aceEditor.getValue()).to.eq('{\n  "key1": "value1"\n}');
            });
        });
    });
});
