describe("Dropdown", () => {
    const seed = "dropdown";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display menu portal", () => {
        cy.visitNewProcess(seed, "testProcess");
        cy.layoutScenario();
        cy.get("[model-id=enricher]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").should("be.visible");
        cy.get("div[class$=singleValue").contains("normal").parent().click();
        cy.get("[data-testid=window]").matchImage();
        const expectedValues = ["normal", "gold"];
        cy.get('.test__menu-portal [role="option"]').each(($el, index, $list) => {
            cy.wrap($el).should("have.text", expectedValues[index]);
            const lastElement = index === $list.length - 1;
            if (lastElement) {
                cy.wrap($el).click();
            }
        });
        //It should be.visible instead of exist, but Cypress think it's covered
        cy.get("div[class$=singleValue").contains("gold").should("exist");
        cy.get("[data-testid=window]").matchImage();
    });
});
