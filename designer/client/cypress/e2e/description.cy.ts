describe("Description", () => {
    const seed = "description";

    before(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    after(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "testProcess");
    });

    it("should display markdown", () => {
        cy.get(`[title="toggle description view"]`).should("not.exist");

        cy.contains(/^properties$/i)
            .should("be.visible")
            .dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("window");

        cy.contains("Description:").next().find(".ace_editor").should("be.visible").click("center").type(`# description header{enter}

*Everything* is going according to **plan**.`);

        cy.get("@window")
            .contains(/^apply$/i)
            .click();

        cy.get(`[title="toggle description view"]`).should("be.visible");
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.reload();

        cy.get(`[title="toggle description view"]`).should("be.visible").click().should("not.exist");

        cy.contains("description header").should("be.visible");
        cy.contains("Everything is going according to plan").should("be.visible").parent().parent().as("description");

        cy.viewport(1200, 600);
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.viewport(1450, 600);
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.get("[title='toggle right panel']").click();
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.get("[title='toggle left panel']").click();
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });
    });
});