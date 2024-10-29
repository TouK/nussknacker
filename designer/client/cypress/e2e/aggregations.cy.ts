describe("aggregations", () => {
    const seed = "aggregations";

    before(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    ["aggregate-sliding", "aggregate-tumbling", "aggregate-session"].forEach((component) => {
        it(`should display updated UI for ${component}`, () => {
            cy.visitNewProcess(seed, "aggregations", "Default");
            cy.getNode(component).dblclick();
            cy.get("[data-testid=window]").should("be.visible");
            cy.get("[title='Aggregations']")
                .parent()
                .should("be.visible")
                .matchImage({
                    screenshotConfig: {
                        padding: [80, 10, 10, 10],
                    },
                });
        });
    });
});
