const verifyTableData = (row: number, col: number, htmlData: unknown) => {
    cy.get('[data-testid="data-grid-canvas"]')
        .find(`[data-testid="glide-cell-${col}-${row - 1}"]`)
        .should("have.html", htmlData);
};

describe("test with events data", () => {
    const seed = "testsWithEventsData";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    //TODO: Test sometimes failed because glide data grid elements overlap each other
    it("should perform test with generated events data", { retries: 3 }, () => {
        cy.viewport("macbook-15");
        cy.visitNewProcess(seed, seed, "DevelopmentTests");
        cy.get('[data-selector="SCENARIO_TEST"]').click();

        // Add row with a default events
        cy.get("[data-testid=window]").as("window");
        cy.get("@window").find("footer").as("testDialogFooter");
        cy.get("@testDialogFooter").contains("button", "Test").should("to.be.disabled");
        cy.addTestRecord(() => {
            const defaultEventForEventGenerator =
                "{\n" +
                '  "input" : {\n' +
                '    "sampleField" : "",\n' +
                '    "dateTime" : "",\n' +
                '    "type" : "",\n' +
                '    "value" : 0\n' +
                "  }\n" +
                "}";

            verifyTableData(1, 2, defaultEventForEventGenerator);
        });
        cy.get("@testDialogFooter").contains("button", "Test").should("not.be.disabled");

        // Generate test data
        cy.get("@window")
            .contains("button", /Append from live data/i)
            .click();

        // Verify if the newly generated data are visible, it's 12 because table header + 10 data rows + table footer + 1 initially added row
        cy.get("@window").find('table[aria-rowcount="13"]');

        cy.get("@testDialogFooter").contains("button", "Test").click();

        // Verify if test mode running
        cy.get('[id="tipsPanel"]').contains("Testing mode enabled");
        // verify if node has 10 test results
        cy.get('[model-id="Event Generator"]').find('[joint-selector="testResultsSummary"]').contains("11");
        cy.get('[data-selector="SCENARIO_TEST"]').click();

        // Rerun test
        cy.intercept("POST", "/api/processManagement/testCase/*").as("retest");
        cy.contains('[data-testid="toolbarButton-label"]', /Rerun test/).click();
        cy.wait("@retest");
    });

    it("should block adding new records if records limit exceeded", () => {
        cy.viewport(1920, 1080);
        cy.visitNewProcess(seed, seed, "DevelopmentTests");
        cy.get('[data-selector="SCENARIO_TEST"]').click();

        cy.get("[data-testid=window]").as("window");
        cy.get("@window").find("footer").as("testDialogFooter");
        cy.get("@testDialogFooter").contains("button", "Test").should("to.be.disabled");
        cy.get("[data-testid=numberOfRecords]").clear().type("14");

        cy.get("@window")
            .contains("button", /Append from live data/i)
            .click();

        cy.get("@testDialogFooter").contains("button", "Test").should("not.be.disabled");

        cy.get("[data-testid=numberOfRecords]").should("have.value", 6);

        cy.get("@window")
            .contains("button", /Append from live data/i)
            .click()
            .click(); // second click fires validation when limit exceeded

        cy.contains('[role="alert"]', /The maximum number of 20 Input data records has been exceeded/).should("be.visible");

        cy.get("@window")
            .contains("button", /Append from live data/i)
            .should("be.disabled");
    });
});
