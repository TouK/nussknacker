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
        cy.get("[data-testid=search-panel]")
            .contains(/^search$/i)
            .click();
        cy.get("[title='toggle left panel']").click();
        cy.get("[data-testid=search-panel]").should("be.not.visible");
        cy.get("#nk-graph-main").click();
        cy.realPress(["Meta", "F"]);
        cy.get("[data-testid=search-panel] input").should("be.visible").should("be.focused");
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
            .dblclick();
        cy.get("[data-testid=window]")
            .contains(/^source$/i)
            .should("be.visible");
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.get("#nk-graph-main").click();
        cy.realPress(["Meta", "F"]);
        cy.get("[data-testid=search-panel] input").should("be.visible").should("be.focused");
        cy.realType("source");
        cy.wait(750); //wait for animation
        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });
});
