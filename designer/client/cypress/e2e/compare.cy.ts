describe("Compare", () => {
    const seed = "compare";
    before(() => {
        cy.viewport("macbook-16");
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.mockWindowDate();
        cy.visitNewProcess(seed, "testProcess");
    });

    it("should fill window", () => {
        cy.contains(/^compare$/i)
            .should("be.visible")
            .should("be.enabled")
            .click();
        cy.contains("Version to compare").get("#otherVersion input").select(0);
        cy.contains("Difference to pick").get("#differentVersion input").select(1);
        cy.contains(/^cancel$/i).should("be.visible");
        // properties validation warnings load asynchronously; without this wait
        // screenshots are taken in an unstable state on slow CI runners
        cy.get("[data-testid=window]")
            .contains(/threads will be used on environment/)
            .should("exist");
        cy.get("[data-testid=window]").matchImage();
        cy.get("button[name=zoom]").click();
        cy.get("[data-testid=window]").matchImage();
        cy.get("button[name=zoom]").click();
        cy.get("[data-testid=window]").matchImage();
    });
});
