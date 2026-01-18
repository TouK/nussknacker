describe("Dropdown", () => {
    const seed = "dropdown";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        // cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display menu portal", () => {
        cy.visitNewProcess(seed, "testProcess");
        cy.layoutScenario();
        cy.openNodeWindow("enricher");
        cy.get("div[class$=singleValue").contains("normal").parent().click();
        cy.get("[data-testid=window]").matchImage();
        const expectedValues = ["normal", "gold"];
        expectedValues.forEach((value, index, $list) => {
            cy.contains(`[role="option"]`, value).should("be.visible").as("option");
            if (index === $list.length - 1) {
                cy.get("@option").click();
            }
        });
        //It should be.visible instead of exist, but Cypress think it's covered
        cy.get("div[class$=singleValue").contains("gold").should("exist");
        cy.get("[data-testid=window]").matchImage();
    });
});
