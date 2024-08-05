describe("Process mouse drag", () => {
    const NAME = "processSelection";
    const snapshotParams: Cypress.MatchImageOptions = {
        maxDiffThreshold: 0.001,
        screenshotConfig: {
            blackout: ["[data-testid='SidePanel']"],
        },
    };

    before(() => {
        cy.deleteAllTestProcesses({ filter: NAME, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: NAME });
    });

    beforeEach(() => {
        cy.visitNewProcess(NAME, "testProcess");
        cy.get("[data-testid=graphPage]", { timeout: 20000 })
            .as("graph")
            .within(() => {
                cy.get("#nk-graph-main svg", { timeout: 20000 }).as("canvas");
            });
        cy.get("[title='toggle left panel']").click();
        cy.layoutScenario();
        cy.wait(500);
    });

    it("should allow pan view", () => {
        cy.get("@canvas").realMouseDown();
        cy.wait(100);
        cy.get("@canvas").realMouseMove(10, 10);
        cy.wait(100);
        cy.get("@canvas").realMouseMove(690, 395);
        cy.wait(100);
        cy.get("@canvas").realMouseUp({ x: 690, y: 395 });
        cy.get("@graph").wait(200).matchImage(snapshotParams);
    });

    it("should select only fully covered (to right)", () => {
        cy.get("@canvas").trigger("keydown", { key: "Meta" });
        cy.get("@canvas").realMouseDown({ metaKey: true, x: 100, y: 100 });
        cy.get("@canvas").realMouseMove(700, 500, { metaKey: true });
        cy.get("@graph").matchImage(snapshotParams);
        cy.get("@canvas").realMouseUp({ x: 700, y: 500 });
        cy.get("@graph").matchImage(snapshotParams);
    });

    it("should select partially covered (to left)", () => {
        cy.get("@canvas").trigger("keydown", { key: "Meta" });
        cy.get("@canvas").realMouseDown({ metaKey: true, x: 700, y: 100 });
        cy.get("@canvas").realMouseMove(300, 500, { metaKey: true });
        cy.get("@graph").matchImage(snapshotParams);
        cy.get("@canvas").realMouseUp({ x: 300, y: 500 });
        cy.get("@graph").matchImage(snapshotParams);
    });

    it("should switch modes, append and inverse select with shift", () => {
        cy.get("@canvas").trigger("keydown", { key: "Meta" });
        cy.get("@canvas").realMouseDown({ metaKey: true, x: 700, y: 100 });
        cy.get("@canvas").realMouseMove(300, 400, { metaKey: true });
        cy.get("@graph").matchImage(snapshotParams);
        cy.get("@canvas").realMouseUp();
        cy.get("@graph").matchImage(snapshotParams);
        cy.get("@canvas").trigger("keydown", { key: "Shift" });
        cy.get("@canvas").realMouseDown({ shiftKey: true, x: 700, y: 150 });
        cy.get("@canvas").realMouseMove(300, 550, { shiftKey: true });
        cy.get("@graph").matchImage(snapshotParams);
        cy.get("@canvas").realMouseUp();
        cy.get("@graph").matchImage(snapshotParams);
    });
});
