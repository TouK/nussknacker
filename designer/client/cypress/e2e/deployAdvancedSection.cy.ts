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
        cy.get('[data-selector="ACTION_DEPLOY"]').siblings().eq(0).click();
        cy.contains("li", /configure & start/i).click();
        cy.get("[data-testid=window]").matchImage();

        cy.contains("log").should("be.visible").click();
        // a click on the window header is on a purpose because we don't want to have a focus on the expandable section
        cy.get("[data-testid=window]").find("h3").click();
        cy.get("[data-testid=window]").matchImage();
    });
});
