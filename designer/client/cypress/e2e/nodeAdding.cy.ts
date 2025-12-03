describe("Node adding", () => {
    const seed = "adding";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.intercept("GET", "/api/processes/*/toolbars", (req) => {
            req.reply({
                id: "test",
                bottomRight: [{ id: "scenario-status-panel" }],
                bottomLeft: [{ id: "activities-panel" }],
                dynamicRight: [
                    {
                        id: "creator-panel-dynamic",
                        disableCollapse: true,
                        additionalParams: { noDrag: "true" },
                    },
                ],
            });
        });
        cy.visitNewProcess(seed);
        cy.get("[data-testid=graphPage]", { timeout: 20000 }).as("graph");
        cy.toggleUserFlag("survey.welcome.closed");
        cy.get("[title='toggle left panel']").click();
    });

    it("should be possible with dynamic panel", () => {
        cy.get("@graph").matchImage();
        cy.contains(/csv.*sql/i)
            .parent()
            .as("toolbar")
            .should("be.not.visible");

        cy.get("[title='add source node']").should("be.visible").click({ force: true });
        cy.contains(/event generator/i).should("be.visible");
        cy.get("@graph").matchImage();

        cy.get("body").realClick({ position: { x: 100, y: 100 } });
        cy.contains(/event generator/i).should("be.not.visible");

        cy.get("[title='add source node']").should("be.visible").click({ force: true });
        cy.contains(/event generator/i).should("be.visible");

        return;

        cy.realPress("{esc}");
        cy.contains(/event generator/i).should("be.not.visible");

        cy.get("[title='add source node']").should("be.visible").click({ force: true });
        cy.contains(/event generator/i).should("be.visible");

        cy.get("@toolbar").find("input").should("be.focused").type("sql");
        cy.contains(/event generator/i).should("not.exist");
        cy.contains(/sql source/i).should("be.visible");
        cy.realPress("Enter");

        cy.get("[title='add source node']").should("not.exist");
        cy.get("[title='add new node']").should("be.visible").click({ force: true });
        cy.contains(/^filter$/i).click();
        cy.getNode("Sql Source").find("circle[port=Out]").click();
        cy.contains(/event generator/i).should("not.exist");
        cy.contains(/dead end/i)
            .should("exist")
            .click({ force: true });
        cy.get("@graph").matchImage();
    });
});
