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
                topRight: [{ id: "search-panel" }],
                bottomRight: [{ id: "scenario-status-panel" }],
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
        cy.window().then((win) => {
            win["$setUserFlag"]("survey.welcome.closed");
        });
    });

    it("should be possible with dynamic panel", () => {
        cy.get("@graph").matchImage();

        // panel hidden
        cy.get(`[placeholder="type here to filter..."]`).as("searchInput").should("exist").should("be.not.visible");

        // panel visible with sources
        cy.get("[title='add source node']").should("be.visible").click({ force: true });
        cy.contains(/event generator/i).should("be.visible");
        cy.get("@graph").matchImage();

        // panel closes by click outside
        cy.get("body").realClick({ position: { x: 100, y: 100 } });
        cy.contains(/event generator/i).should("be.not.visible");

        // panel closes by esc
        cy.get("[title='add source node']").should("be.visible").click({ force: true });
        cy.contains(/event generator/i).should("be.visible");
        cy.realPress("{esc}");
        cy.contains(/event generator/i).should("be.not.visible");

        // source node added by click
        cy.get("[title='add source node']").should("be.visible").click({ force: true });
        cy.contains(/event generator/i).should("be.visible");
        cy.wait(500);
        cy.get("@searchInput").should("be.focused");
        cy.get("@searchInput").click().type("sql");
        return;
        cy.contains(/event generator/i).should("not.exist");
        cy.contains(/sql source/i)
            .should("be.visible")
            .click();
        cy.wait(500);

        // placeholder button hidden, node added
        cy.get("[title='add source node']").should("not.exist");
        cy.get("[title='add new node']").should("be.visible").click({ force: true });
        cy.contains(/^filter$/i).click();
        cy.wait(500);

        // node adding by port click, sources hidden
        cy.getNode("Sql Source").find(`circle[port="Out"]`).click();
        cy.contains(/event generator/i).should("not.exist");
        cy.contains(/dead end/i)
            .should("exist")
            .click({ force: true });
        cy.wait(500);

        // node adding by search and enter
        cy.getNode("Filter").find(`circle[port="Out"]`).click();
        cy.get("@searchInput")
            .should("be.focused")
            .type("{enter}") // nothing happens
            .type("record variable")
            .type("{enter}"); // only component added

        // node adding with link drag
        cy.getNode("Record Variable").find(`circle[port="Out"]`).dndTo("[data-testid=graphPage]", { x: -200, y: 500 });
        cy.contains(/delay/i).should("exist").click({ force: true });
        cy.wait(500);

        // node adding with in port click
        cy.getNode("Filter").find(`circle[port="In"]`).click();
        cy.contains(/choice/i)
            .should("exist")
            .click({ force: true });
        cy.wait(500);

        cy.get("@graph").matchImage();
    });
});
