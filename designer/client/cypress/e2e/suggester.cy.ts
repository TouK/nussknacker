describe("Expression suggester", () => {
    const seed = "suggester";

    before(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display colorfull and sorted completions", () => {
        cy.visitNewProcess(seed, "variables");
        cy.layoutScenario();
        cy.get("[model-id=kafka-string]").trigger("dblclick");
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title=Value]").next().find(".ace_editor").click().type(".").contains(/\.$/);
        cy.get(".ace_autocomplete")
            .should("be.visible")
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [25, 1, 1] },
            });
        cy.get("[title=Value]").next().find(".ace_editor").click().type("c").contains(/\.c$/);
        cy.get(".ace_autocomplete")
            .should("be.visible")
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [25, 1, 1] },
            });
    });

    it("should display javadocs", () => {
        cy.viewport(1600, 1200);
        cy.visitNewProcess(seed, "variables");
        cy.get("[model-id=kafka-string]").trigger("dblclick");
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title=Value]").next().find(".ace_editor").click().type("{selectall}#DATE_FORMAT.parseLocalDat");
        cy.get(".ace_autocomplete").should("be.visible");
        cy.get("[data-testid=window]").matchImage();
        cy.get(".ace_editor .ace_tooltip").matchImage();
        // We wait for validation result to be sure that red message below the form field will be visible
        cy.press("Enter");
        cy.get(".ace_autocomplete").should("not.be.visible");
        cy.contains("Mismatch parameter types").should("exist");
    });

    it("should display completions for second line (bugfix)", () => {
        cy.visitNewProcess(seed, "variables");
        cy.layoutScenario();
        cy.get("[model-id=kafka-string]").trigger("dblclick");
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title=Value]").next().find(".ace_editor").click().type(" +{enter}#").contains(/^.$/m);
        cy.get(".ace_autocomplete")
            .should("be.visible")
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [45, 1, 1] },
            });
    });
});
