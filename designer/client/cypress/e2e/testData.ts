describe("test data", () => {
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
        cy.toggleUserFlag("node.showTestingTab", true);

        cy.openNodeWindow("Event Generator");
        cy.openNodeDetailsTestingTab();
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
        cy.get("[data-testid=window]").as("window");
        // Generate test data
        cy.get("@window")
            .contains("button", /Append from live data/i)
            .click();

        // Verify if the newly generated data are visible, it's 12 because table header + 10 data rows + table footer + 1 initially added row
        cy.get("@window").find('table[aria-rowcount="13"]');
    });

    it("should block adding new records if records limit exceeded", () => {
        cy.viewport(1920, 1080);
        cy.visitNewProcess(seed, seed, "DevelopmentTests");
        cy.toggleUserFlag("node.showTestingTab", true);

        cy.openNodeWindow("Event Generator");
        cy.openNodeDetailsTestingTab();

        cy.get("[data-testid=numberOfRecords]").clear().type("14");

        cy.get("[data-testid=window]").as("window");
        cy.get("@window")
            .contains("button", /Append from live data/i)
            .click();

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

const verifyTableData = (row: number, col: number, htmlData: unknown) => {
    cy.get('[data-testid="data-grid-canvas"]')
        .find(`[data-testid="glide-cell-${col}-${row - 1}"]`)
        .should("have.html", htmlData);
};
