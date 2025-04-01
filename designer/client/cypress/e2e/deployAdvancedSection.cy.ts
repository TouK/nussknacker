describe("Deploy advanced section", () => {
    const seed = "deployAdvancedSection";

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

    it("should display and expand advanced deploy section", () => {
        cy.contains(/^deploy$/i).click();
        cy.get("[data-testid=window]").matchImage();

        cy.contains("log").should("be.visible").click();
        cy.get("[data-testid=window]").matchImage();
    });
});
