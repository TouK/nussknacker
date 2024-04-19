describe("Process tests from file", () => {
    const transactionsTopic = "transactions";
    const scenarioName = "test-from-file-scenario";

    before(() => {
        clear();
    });

    after(() => {
        clear();
    });

    it("should properly display results of tests from file", () => {
        cy.viewport(1440, 1200);
        cy.createSchema(`${transactionsTopic}-value`, "transactionsAvroSchema.json");
        cy.createKafkaTopic(transactionsTopic);
        cy.visitNewProcess(scenarioName, "dumbStreamKafkaSourceScenario", "DevelopmentTests");
        cy.get("[title='run test on data from file']")
            .first()
            .next("[type=file]")
            .should("exist")
            .selectFile("cypress/fixtures/transactionsTestData.txt", { force: true });
        cy.get("text[joint-selector='testResultsSummary']").eq(0).contains("1");
        cy.get("[model-id='kafka']").should("be.visible").trigger("dblclick");
        cy.get("[data-testid='window-frame']").matchImage();
    });

    function clear() {
        cy.log("Clearing data...");
        cy.deleteAllTestProcesses({ filter: scenarioName, force: true });
        cy.removeSchema(`${transactionsTopic}-value`);
        cy.removeKafkaTopic(transactionsTopic);
    }
});
