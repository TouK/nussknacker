describe("Process", () => {
    const seed = "process";
    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    describe("with data", () => {
        beforeEach(() => {
            cy.visitNewProcess(seed, "testProcess");
        });

        it("should allow editing choice edge expression", () => {
            cy.layoutScenario();
            cy.contains(/^base$/)
                .should("exist")
                .scrollIntoView();
            cy.contains(/^choice$/)
                .should("be.visible")
                .drag("#nk-graph-main", {
                    target: {
                        x: 580,
                        y: 450,
                    },
                    force: true,
                });
            cy.layoutScenario();
            cy.get("[model-id$=choice-sendSms-true]").should("be.visible").trigger("dblclick");

            cy.get("[data-testid=window]").should("be.visible");
            cy.get("[data-testid=window]").find("[data-testid='fieldsRow:0']").find(".ace_editor").as("input");
            cy.get("[data-testid=window]").matchImage();
            cy.get("@input").click().type(" || false");
            cy.contains(/^apply/i)
                .should("be.enabled")
                .click();
            cy.get("[data-testid=window]").should("not.exist");
            cy.get("[data-testid=graphPage]").matchImage({
                screenshotConfig: {
                    blackout: ["> :not(#nk-graph-main) > div"],
                },
            });
        });
    });
});
