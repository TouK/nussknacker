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

        cy.get("[data-testid=window]").find("[data-testid=expandable-header]").contains("log").click();
        // a click on the window header is on a purpose because we don't want to have a focus on the expandable section
        cy.get("[data-testid=window]").find("h3").click();
        cy.get("[data-testid=window]").matchImage();
    });
});
