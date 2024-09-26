describe("Activities", () => {
    const seed = "activities";

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

    it("should display activities", () => {
        // Compare action
        cy.contains("Activities").should("exist").scrollIntoView();
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible").find("input").eq(1).click().type("100");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        cy.contains(/^save/i).should("be.enabled").click();
        cy.get("[data-testid=window]").should("be.visible").find("textarea").click().type("test comment");
        cy.contains(/^ok/i).should("be.enabled").click();

        //TODO: To remove when activities automatically updated
        cy.reload();
        cy.get("[data-testid=compare-2]").click();

        cy.contains("Version to compare").siblings().as("versionToCompare");
        cy.get("@versionToCompare").contains("2 - created by admin 2024-01-04|12:10");
        cy.get("@versionToCompare").find("input").should("be.disabled");
        cy.contains("Difference to pick").get("#differentVersion input").select(1);
    });
});
