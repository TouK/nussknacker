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

    it.skip("should display rich table editor", () => {
        cy.viewport("macbook-15");
        cy.visitNewProcess(seed, "table", "Default");
        cy.intercept("POST", "/api/nodes/*/validation", (request) => {
            if (request.body.nodeData.service?.parameters.find((p) => p.name === "Expression")) {
                request.alias = "validation";
            }
        });

        cy.getNode("decision-table").dblclick();
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
        cy.realType("2.0");
        cy.realPress("Enter");
        cy.get("@table").click(580, 25).click(580, 25);
        cy.get("@table").click(350, 125).click(350, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("true").realPress("Tab");
        cy.realType("bar").realPress("Enter");
        cy.realPress("Enter");
        cy.realPress("Escape");
        cy.realType("xxx");
        cy.get("@table")
            .realMouseMove(210, 50)
            .realMouseDown({
                x: 210,
                y: 50,
            })
            .realMouseMove(210, 50)
            .realMouseUp({
                x: 210,
                y: 50,
            });
        cy.get("@table").dblclick(381, 50);
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

        cy.get("@table").click(230, 50);
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

        // menu on number
        cy.get("@table").rightclick(15, 125);
        cy.contains(/^remove 1 row$/i).click();

        // menu on header
        cy.get("@table").rightclick(50, 15);
        cy.contains(/^remove 1 column$/i).click();

        // menu on cell
        cy.get("@table").rightclick(50, 125);
        cy.contains(/^remove 1 column$/i).click();
        cy.get("@table").rightclick(50, 125);
        cy.contains(/^remove 1 row$/i).click();
        cy.get("@table").rightclick(50, 90);
        cy.contains(/^remove 1 column$/i).click();
        snapshot();

        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.contains(`There is no property 'some name' in type`).should("be.visible");
    });

    // For now, it's a separate test. However, we can merge it should display rich table editor when fixed
    // TODO: Fix flaky tests on CI
    it.skip("should change columns position", () => {
        cy.viewport("macbook-15");
        cy.visitNewProcess(seed, "table", "Default");
        cy.intercept("POST", "/api/nodes/*/validation", (request) => {
            if (request.body.nodeData.service?.parameters.find((p) => p.name === "Expression")) {
                request.alias = "validation";
            }
        });

        cy.getNode("decision-table").dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("modal");
        cy.get("[title='Decision Table']").next().as("editor");
        cy.get("[data-testid='table-container']").should("be.visible").as("table");

        // Provide data to the table
        cy.get("@table").click(100, 50);
        cy.get("[role='menuitem']").contains("Double").click();
        cy.get("@table").click(550, 18);
        cy.realType("some name");
        cy.realPress("Enter");
        cy.get("@table").click(100, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("2.0");
        cy.realPress("Enter");
        cy.get("@table").click(580, 25).click(580, 25);
        cy.get("@table").click(350, 125).click(350, 125);
        cy.get("#portal textarea").should("be.visible");
        cy.realType("true").realPress("Tab");
        cy.realType("bar").realPress("Enter");
        cy.realPress("Enter");
        cy.realPress("Escape");
        cy.realType("xxx");

        // Add date picker field
        cy.get("@table").click(580, 25);
        cy.get("@table").click(550, 50);
        cy.get("[role='menuitem']").contains("LocalDate").click();

        // Check if date picker is visible
        cy.get("@table").click(550, 125).click(550, 125).click(550, 125);
        cy.get("#portal input").type("2023-02-02");
        cy.get("@modal").matchImage();
        cy.realPress("Enter");

        // Add datetime picker field
        cy.get("@table").click(580, 25);
        cy.get("@table").click(550, 50);
        cy.get("[role='menuitem']").contains("LocalDateTime").click();

        // Check if datetime picker is visible
        cy.get("@table").click(550, 125).click(550, 125).click(550, 125);
        cy.get("#portal input").type("2023-02-02T12:40:01");
        cy.get("@modal").matchImage();
        cy.realPress("Enter");

        // Move the last column to the first place
        cy.get("@table").realMouseDown({ x: 400, y: 40 }).realMouseMove(50, 40).realMouseUp();
        snapshot();

        //FIXME: for now, clicking the apply changes button makes the test run forever, even when the test is green.
        // Looks like it's a problem with real events.

        // // Reopen the node and verify if position is persisted
        // cy.contains(/^apply/i)
        //     .should("be.enabled")
        //     .click();
        // cy.getNode("decision-table").dblclick();
        // snapshot();
    });
});
