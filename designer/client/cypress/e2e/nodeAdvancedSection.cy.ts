describe("Nodes advanced section", () => {
    const seed = "nodesAdvancedSection";

    before(() => {
        cy.viewport("macbook-16");
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "advancedSectionAndDeployParam");
    });

    it("should display and expand advanced section", () => {
        cy.getNode("Log").dblclick();
        cy.get("[data-testid=window]").should("be.visible");

        cy.contains("Advanced parameters").should("be.visible").click();
        cy.get("[data-testid=window]").matchImage();

        cy.contains("Advanced parameters").click();
        cy.get("[data-testid=window]").matchImage();
    });
});
