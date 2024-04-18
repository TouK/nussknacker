describe("Process tests from file", () => {
    const peopleTopic = "people";

    const scenarioName = "test-from-file-scenario";

    before(() => {
        clear();
    });

    after(() => {
        clear();
    });

    it("should properly display results of tests from file", () => {
        cy.viewport(1440, 1200);
        cy.createSchema(`${peopleTopic}-value`, "personAvroSchema.json");
        cy.createKafkaTopic(peopleTopic);
        cy.visitNewProcess(scenarioName, "dumbFlinkKafkaScenario");
        cy.get("[title='run test on data from file']")
            .first()
            .next("[type=file]")
            .should("exist")
            .selectFile("cypress/fixtures/personKafkaSourceTestData.txt", { force: true });
        cy.get("text[joint-selector='testResultsSummary']").eq(0).contains("1");
        cy.get("[model-id='kafka']").should("be.visible").trigger("dblclick");
        cy.get("[data-testid='window-frame']").matchImage();
    });

    function clear() {
        cy.log("Clearing data...");
        cy.deleteAllTestProcesses({ filter: scenarioName, force: true });
        cy.removeSchema(`${peopleTopic}-value`);
        cy.removeKafkaTopic(peopleTopic);
    }
});
