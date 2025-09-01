const verifyTableData = (row: number, col: number, htmlData: unknown) => {
    cy.get('[data-testid="data-grid-canvas"]')
        .find(`[data-testid="glide-cell-${col}-${row - 1}"]`)
        .should("have.html", htmlData);
};

describe("test with events data", () => {
    const seed = "testsWithEventsData";

    beforeEach(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    afterEach(() => {
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

        cy.get('[data-testid="data-grid-canvas"]')
            .as("canvasTable")
            .should("be.visible")
            .then(($canvas) => {
                const rect = $canvas[0].getBoundingClientRect();
                // Click near bottom right corner
                const clickX = rect.width - 10;
                const clickY = rect.height - 10;

                cy.wrap($canvas).click(clickX, clickY, { force: true });
                const defaultEventForEventGenerator =
                    "{\n" +
                    '  "input" : {\n' +
                    '    "sampleField" : "",\n' +
                    '    "dateTime" : "",\n' +
                    '    "type" : "",\n' +
                    '    "value" : 0\n' +
                    "  }\n" +
                    "}";

                verifyTableData(1, 3, defaultEventForEventGenerator);
            });
        cy.get("@testDialogFooter").contains("button", "Test").should("not.be.disabled");

        // Generate test data
        cy.get("@testDialogFooter")
            .contains("button", /Generate Test Data/i)
            .click();

        // Verify if the newly generated data are visible, it's 12 because table header + 10 data rows + table footer
        cy.get("@window").find('table[aria-rowcount="12"]');

        cy.get("@testDialogFooter").contains("button", "Test").click();

        // Verify if test mode running
        cy.get('[id="tipsPanel"]').contains("Testing mode enabled");
        // verify if node has 10 test results
        cy.get('[model-id="Event Generator"]').find('[joint-selector="testResultsSummary"').contains("10");
        cy.get('[data-selector="SCENARIO_TEST"]').click();

        // Rerun test
        cy.intercept("POST", "/api/processManagement/test/*").as("retest");
        cy.contains('[data-testid="toolbarButton-label"]', /Rerun test/).click();
        cy.wait("@retest");
    });
});
