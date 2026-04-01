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
        cy.getNode("kafka-string").trigger("dblclick");
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
        cy.getNode("kafka-string").trigger("dblclick");
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title=Value]").next().find(".ace_editor").click().type("{selectall}#DATE_FORMAT.parseLocalDat");
        cy.get(".ace_autocomplete").should("be.visible");
        cy.get("[data-testid=window]").matchImage();
        cy.get(".ace_editor .ace_tooltip").matchImage();
        // We wait for validation result to be sure that red message below the form field will be visible
        cy.get("[title=Value]").click(); // blur;
        cy.get(".ace_autocomplete").should("not.be.visible");
        cy.contains("There is no property").should("exist");
    });

    it("should insert bracket access for suggestion with space", () => {
        cy.visitNewProcess(seed, "variables");
        cy.layoutScenario();
        cy.getNode("kafka-string").trigger("dblclick");
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title=Value]").next().find(".ace_editor").click().type(".");
        cy.get(".ace_autocomplete").should("be.visible").contains("with space").should("be.visible").click({ force: true });
        cy.get("[title=Value]").next().find(".ace_editor").should("contain", '["with space"]');
    });

    it("should not offer bracket access suggestions for double dot", () => {
        cy.visitNewProcess(seed, "variables");
        cy.layoutScenario();
        cy.getNode("kafka-string").trigger("dblclick");
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title=Value]").next().find(".ace_editor").click().as("editor");
        cy.get("@editor")
            .type(".")
            .contains(/[^.]\.$/);
        cy.get(".ace_autocomplete").should("be.visible");
        cy.get("@editor")
            .type(".")
            .contains(/[^.]\.\.$/);
        cy.get(".ace_autocomplete").should("not.be.visible");
    });

    it("should display completions for second line (bugfix)", () => {
        cy.visitNewProcess(seed, "variables");
        cy.layoutScenario();
        cy.getNode("kafka-string").trigger("dblclick");
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
