describe("Node window", () => {
    const NAME = "node-window";

    before(() => {
        cy.deleteAllTestProcesses({ filter: NAME, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: NAME });
    });

    beforeEach(() => {
        cy.viewport(1500, 800);
        cy.visitNewProcess(NAME).as("processName");
    });

    it("should display periodic source", () => {
        cy.contains(/^sources$/)
            .should("be.visible")
            .click();
        cy.get("[data-testid='component:periodic']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 800,
                    y: 300,
                },
                force: true,
            });

        cy.getNode("periodic").dblclick();

        // TODO: fix validation display in node windows
        cy.intercept("POST", "/api/nodes/*/validation").as("validation");
        cy.wait("@validation");

        cy.get("[data-testid=window]").should("be.visible");
        cy.contains(/^hours$/).should("be.visible");
        cy.get("[data-testid=window]").matchImage();
    });
});
