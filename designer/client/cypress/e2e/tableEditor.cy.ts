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
        cy.visitNewProcess(seed, "table");

        cy.getNode("decisionTableCustomNode").dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("modal");
        cy.get("[title='tableData']").next().as("editor");
        cy.get("[data-testid='table-container']").should("be.visible").as("table");
        snapshot();

        cy.get("@table").click(100, 50);
        cy.get("[value='java.lang.Double']").click();
        cy.get("@table").click(500, 15);
        cy.realType("some name{enter}", { delay: 50 });

        cy.get("@table").click(100, 125);
        cy.realType("hello world{enter}", { delay: 50 });
        cy.get("@table").click(550, 25).click(550, 25);
        cy.get("@table").click(350, 125).click(350, 125);
        cy.realType("foo", { delay: 50 });
        cy.focused().tab();
        cy.wait(50).realType("bar{enter}", { delay: 50 });
        cy.wait(50).realType("{enter}", { delay: 50 });
        cy.wait(50).realType("xxx{enter}", { delay: 50 });

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
