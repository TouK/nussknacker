describe("Additional info", () => {
    const seed = "additionalInfo";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed).as("processName");
    });

    it("should have visual password sanitizer", () => {
        cy.intercept("POST", "/api/properties/*/additionalInfo", {
            body: {
                content: "extremely safe <sanitizd-passwrd>secret</sanitizd-passwrd> content",
                type: "MarkdownAdditionalInfo",
            },
        });

        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible");

        cy.clock();
        cy.contains("extremely safe").should("exist").scrollIntoView();
        cy.contains("secret").should("not.exist");
        cy.contains("••••••").should("be.visible");
        cy.get("button[aria-label=reveal]").should("be.visible").click();
        cy.contains("secret").should("be.visible");
        cy.tick(15000);
        cy.contains("secret").should("not.exist");
        cy.contains("••••••").should("be.visible");
    });
});
