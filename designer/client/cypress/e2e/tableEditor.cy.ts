function snapshot() {
    cy.get("[data-testid='window']").matchImage();
}

describe("Table editor", () => {
    const seed = "table";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should display rich table editor", () => {
        cy.viewport("macbook-15");
        cy.visitNewProcess(seed, "table");

        cy.getNode("decisionTableCustomNode").dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("modal");
        cy.get("[title='tableData']").next().as("editor");
        cy.get("[data-testid='table-container']").should("be.visible").as("table");
        snapshot();

        cy.get("@table").click(100, 50);
        cy.get("[value='java.lang.Double']").click();
        cy.get("@table").click(500, 18);
        cy.realType("some name");
        cy.realPress("Enter");

        cy.get("@table").click(100, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("hello world");
        cy.realPress("Enter");
        cy.get("@table").click(550, 25).click(550, 25);
        cy.get("@table").click(350, 125).click(350, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("foo").realPress("Tab");
        cy.realType("bar").realPress("Enter");
        cy.realPress("Enter");
        cy.realPress("Escape");
        cy.realType("xxx");

        cy.get("[title='Switch to expression mode']").should("be.enabled").click();
        cy.get("@table").should("not.exist");
        snapshot();

        cy.get("[title='Switch to table mode']").should("be.enabled").click();
        cy.get("[data-testid='table-container']").should("be.visible").as("table");
        snapshot();

        cy.get("@table").rightclick(15, 125);
        cy.contains(/^remove row$/i).click();
        cy.get("@table").rightclick(480, 15);
        cy.contains(/^remove column$/i).click();
        cy.get("@table").rightclick(480, 125);
        cy.contains(/^remove column$/i).click();
        cy.get("@table").rightclick(480, 125);
        cy.contains(/^remove row$/i).click();
        cy.get("@table").rightclick(480, 90);
        cy.contains(/^remove column$/i).click();
        snapshot();

        cy.get("[title='Switch to expression mode']").should("be.enabled").click();
        cy.get("@table").should("not.exist");
        snapshot();
    });
});
