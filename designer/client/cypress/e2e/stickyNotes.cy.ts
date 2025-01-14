describe("Sticky notes", () => {
    const seed = "stickyNotes";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "stickyNotes");
    });

    const screenshotOptions: Cypress.MatchImageOptions = {
        screenshotConfig: { clip: { x: 0, y: 0, width: 1400, height: 600 } },
    };

    it("should allow to drag sticky note", () => {
        cy.layoutScenario();
        cy.contains(/^sticky notes$/i)
            .should("exist")
            .scrollIntoView();
        cy.get("[data-testid='component:sticky note']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 600,
                    y: 300,
                },
                force: true,
            });

        cy.get("[data-testid=graphPage]").matchImage(screenshotOptions);
    });

    it("should add text to note and display it as markdown", () => {
        cy.layoutScenario();
        cy.contains(/^sticky notes$/i)
            .should("exist")
            .scrollIntoView();
        cy.get("[data-testid='component:sticky note']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 600,
                    y: 300,
                },
                force: true,
            });
        cy.get(".sticky-note-content").dblclick();
        cy.get(".sticky-note-content textarea").type("# Title\n- p1\n- p2\n\n[link](href)");
        cy.get("[model-id='request']").click();
        cy.get("[data-testid=graphPage]").matchImage(screenshotOptions);
    });

    it("should disable sticky note when scenario is not saved", () => {
        cy.layoutScenario();
        cy.contains(/^sticky notes$/i)
            .should("exist")
            .scrollIntoView();

        cy.dragNode("request", { x: 600, y: 300 });

        cy.get("[data-testid='component:sticky note']").should("have.class", "tool disabled");
        cy.get("[data-testid=graphPage]").matchImage(screenshotOptions);
    });
});
