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
        cy.visitNewProcess(seed, "table", "Default");
        cy.intercept("POST", "/api/nodes/*/validation", (request) => {
            if (request.body.nodeData.service?.parameters.find((p) => p.name === "Expression")) {
                request.alias = "validation";
            }
        });

        // check default table view
        cy.getNode("decision-table").dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("modal");
        cy.get("[title='Basic Decision Table']").next().as("editor");
        cy.get("[data-testid='table-container']").should("be.visible").as("table");
        snapshot();

        // switch first column type
        cy.get("@table").click(100, 50);
        cy.get("[role='menuitem']").contains("Double").click();

        // change first column name
        cy.get("@table").click(550, 18);
        cy.realType("some name");
        cy.realPress("Enter");

        // add new row with value in A2
        cy.get("@table").click(100, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("2.0");
        cy.realPress("Enter");

        // add two new columns with "+" button
        cy.get("@table").click(585, 25).click(585, 25);

        // add value to B2
        cy.get("@table").click(350, 125).click(350, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("true");

        // move to C2
        cy.realPress("Tab");

        // edit C2
        cy.realType("bar").realPress("Enter");

        // add C3 by key and exit editor
        cy.realPress("Enter");
        cy.realPress("Escape");

        // edit C3
        cy.realType("xxx");

        // add new column with "+" button
        cy.get("@table").click(585, 25);

        snapshot();

        cy.get("[title=Expression]").next().find(".ace_editor").as("expr");

        // check autocomplete values (#ROW type)
        cy.get("@expr").click().type("{selectall}#ROW.").contains(/.$/);
        cy.get(".ace_autocomplete")
            .should("be.visible")
            .matchImage({
                maxDiffThreshold: 0.0025,
                screenshotConfig: { padding: [25, 1, 1] },
            });

        // check column type validation in 'Expression'
        cy.get("@expr").type("B");
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`Bad expression type, expected: Boolean, found: String`).should("be.visible");

        // change B type and check validation in 'Expression'
        cy.get("@table").click(250, 50);
        cy.get("[role='menuitem']").contains("Boolean").click();
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`Bad expression type, expected: Boolean, found: String`).should("not.be.visible");

        // check autocomplete values (#ROW type, index access)
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

        // remove 2nd row by menu on number
        cy.get("@table").rightclick(15, 125);
        cy.contains(/^remove 1 row$/i).click();

        // remove 1st column by menu on header
        cy.get("@table").rightclick(50, 15);
        cy.contains(/^remove 1 column$/i).click();

        // remove 1st column by menu on cell B2
        cy.get("@table").rightclick(50, 125);
        cy.contains(/^remove 1 column$/i).click();

        // remove 2nd row by menu on cell C2
        cy.get("@table").rightclick(50, 125);
        cy.contains(/^remove 1 row$/i).click();

        // remove 2nd column by menu on cell D2
        cy.get("@table").rightclick(480, 90);
        cy.contains(/^remove 1 column$/i).click();

        // remove only column by menu on only cell (auto create column A)
        cy.get("@table").rightclick(480, 90);
        cy.contains(/^remove 1 column$/i).click();
        snapshot();

        // check column type validation in 'Expression'
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`There is no property 'some name' in type`).should("be.visible");
    });
});
