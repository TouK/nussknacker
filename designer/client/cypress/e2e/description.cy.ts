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

        cy.contains("Description").next().find(".ace_editor").should("be.visible").click("center").type(`# description header{enter}

*Everything* is going according to **plan**.`);

        cy.contains("Show description each time scenario is opened").click();

        cy.get("@window")
            .contains(/^apply$/i)
            .click();

        cy.get(`[title="toggle description view"]`).should("be.visible").click();
        cy.contains("description header").should("be.visible");
        cy.get("[data-testid=window]").should("be.visible").find("header").find("[name=close]").click();

        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.reload();

        cy.contains("Everything is going according to plan").should("be.visible");

        cy.viewport(1200, 800);
        cy.get("[data-testid=window]").matchImage({ screenshotConfig: { padding: [20, 100] } });
    });
});
