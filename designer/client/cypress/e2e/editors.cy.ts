describe("Editors", () => {
    const seed = "editors";
    before(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display SpEL editor with editor switch to SpEL Template Editor", () => {
        cy.visitNewProcess(seed, undefined, "Category1");

        cy.contains("Classinstancesource")
            .last()
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 840,
                    y: 600,
                },
                force: true,
            });
        cy.layoutScenario();
        cy.intercept("POST", "/api/nodes/*/validation");

        cy.openNodeWindow("Classinstancesource").as("window");

        // Expression verification
        cy.get("@window")
            .contains(/Additional class/i)
            .siblings()
            .find("[id='ace-editor']")
            .type("{backspace}{backspace}"); //clear field

        // Open SpEL template editor hint
        cy.get("@window")
            .contains(/Additional class/i)
            .siblings()
            .find("[data-testid='InfoIcon']")
            .click();

        // Wait for a tooltip rendering and positioning
        cy.contains("You are using a string-template-based input");
        cy.wait(200);
        cy.get("[data-testid=window]").matchImage();

        cy.get("[role=tab]").contains("expression").click();

        // Open SpEL editor hint
        cy.get("@window")
            .contains(/Additional class/i)
            .siblings()
            .find("[data-testid='InfoIcon']")
            .click();

        // Wait for a tooltip rendering and positioning
        cy.contains("You are using an expression-based input");
        cy.wait(200);
        cy.get("@window").matchImage();
    });
});
