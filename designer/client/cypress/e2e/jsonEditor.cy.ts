describe("JSON editor", () => {
    const seed = "json";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display colorfull json", () => {
        cy.visitNewProcess(seed, "rr", "RequestResponse");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").as("modal");
        cy.get("[title='Input schema']").next().find(".ace_editor").should("be.visible").parent().as("editor");

        cy.get("@editor").matchImage({
            maxDiffThreshold: 0.0025,
            screenshotConfig: { padding: [1] },
        });

        cy.get("@editor")
            .click("center")
            .type("{enter}false")
            .wait(300)
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [1, 1, 24, 1] },
            });
    });
});
