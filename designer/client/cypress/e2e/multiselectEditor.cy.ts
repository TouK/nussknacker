describe("Multiselect fixed editor", () => {
    const seed = "multiselect";
    const maxDiffThreshold = 0.0001;

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display wrong data", () => {
        cy.visitNewProcess(seed, "multiselect_wrong");
        cy.getNode("Service").should("be.visible").trigger("dblclick");
        cy.get("[title='multiSelectParam']").next().should("be.visible").as("editor");
        cy.get("@editor").matchImage({ maxDiffThreshold });

        // force switch mode to display invalid data
        cy.toggleUserFlag("editor.allowForceSwitch", true);
        cy.get("@editor").contains("button", "multiple fixed values").click({ altKey: true });
        cy.get("@editor").matchImage({ maxDiffThreshold });
    });

    it("should show chips with selected values", () => {
        cy.visitNewProcess(seed, "multiselect_empty");
        cy.getNode("Service").should("be.visible").trigger("dblclick");
        cy.get("[title='multiSelectParam']").next().should("be.visible").as("editor");
        cy.get("@editor").matchImage({ maxDiffThreshold });

        cy.get("@editor").find("input").parent().as("select");
        cy.get("@select").click();
        cy.contains("option1").should("be.visible").click();
        cy.contains("option2").should("be.visible").click();
        cy.get("body").click(5, 5); //click outside;
        cy.get("@editor").matchImage({ maxDiffThreshold });

        cy.get("@editor").contains("button", "json").click();
        cy.get("@editor").matchImage({ maxDiffThreshold });
        cy.get("@editor").contains("button", "multiple fixed values").click();

        cy.contains("[role='button']", "option1").should("be.visible").find('[data-testid="CancelIcon"]').click();
        cy.contains("[role='button']", "option2").should("be.visible").find('[data-testid="CancelIcon"]').click();
        cy.contains("option1").should("not.exist");
        cy.contains("option2").should("not.exist");
        cy.get("@editor").matchImage({ maxDiffThreshold });
    });

    it("should search and group values", () => {
        cy.visitNewProcess(seed, "multiselect_empty");
        cy.getNode("Service").should("be.visible").trigger("dblclick");
        cy.get("[title='multiSelectGroupingParam']").next().should("be.visible").as("editor");

        cy.get("@editor").find("input").parent().as("select");
        cy.get("@select").click().type("quasi");
        cy.get("@editor").matchImage({ maxDiffThreshold, screenshotConfig: { padding: [10, 10, 350, 10] } });
        cy.get("@select").type("{enter}");
        cy.get("@editor").matchImage({ maxDiffThreshold, screenshotConfig: { padding: [10, 10, 350, 10] } });
    });
});
