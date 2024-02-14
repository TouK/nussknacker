function snapshot() {
    cy.get("@editor").matchImage();
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
        cy.intercept("POST", "/api/nodes/*/validation", (request) => {
            if (request.body.nodeData.service?.parameters.find((p) => p.name === "Expression")) {
                request.alias = "validation";
            }
        });

        cy.getNode("decisionTableCustomNode").dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("modal");
        cy.get("[title='Basic Decision Table']").next().as("editor");
        cy.get("[data-testid='table-container']").should("be.visible").as("table");
        snapshot();

        cy.get("@table").click(100, 50);
        cy.get("[role='menuitem']").contains("Double").click();
        cy.get("@table").click(550, 18);
        cy.realType("some name");
        cy.realPress("Enter");

        cy.get("@table").click(100, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("hello world");
        cy.realPress("Enter");
        cy.get("@table").click(580, 25).click(580, 25);
        cy.get("@table").click(350, 125).click(350, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("foo").realPress("Tab");
        cy.realType("bar").realPress("Enter");
        cy.realPress("Enter");
        cy.realPress("Escape");
        cy.realType("xxx");
        snapshot();

        cy.get("[title=Expression]").next().find(".ace_editor").as("expr");

        cy.get("@expr").click().type("{selectall}#ROW.").contains(/.$/);
        cy.get(".ace_autocomplete")
            .should("be.visible")
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [25, 1, 1] },
            });
        cy.get("@expr").type("B");
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`Bad expression type, expected: Boolean, found: String`).should("be.visible");

        cy.get("@table").click(350, 50);
        cy.get("[role='menuitem']").contains("Boolean").click();
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`Bad expression type, expected: Boolean, found: String`).should("not.be.visible");

        cy.get("@expr").click().type(`{selectall}#ROW["{ctrl} `);
        cy.get(".ace_autocomplete")
            .should("be.visible")
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [25, 1, 1] },
            });
        cy.get("@expr").type(`{leftarrow}{leftarrow}some name`);
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`Bad expression type, expected: Boolean, found: Double`).should("be.visible");

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

        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`There is no property 'some name' in type`).should("be.visible");
    });
});
