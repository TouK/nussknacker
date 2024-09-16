describe("Process view", () => {
    const seed = "process";

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

    it("should have node search toolbar", () => {
        cy.get("[data-testid=search-panel]").should("be.visible");
        cy.get("[data-testid=search-panel]").contains(/^search$/i);
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click();
        cy.realType("en");
        cy.get("[data-testid=search-panel]").contains(/sms/i).click();
        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
        cy.get("[data-testid=search-panel]")
            .scrollIntoView()
            .matchImage({ diffConfig: { threshold: 0.08 } });
        cy.get("[data-testid=search-panel]")
            .contains(/source/i)
            .click()
            .click();
        cy.get("[data-testid=window]")
            .contains(/^source$/i)
            .should("be.visible");
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click().clear();
        cy.realType("source");
        cy.wait(750); //wait for animation
        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });
});
