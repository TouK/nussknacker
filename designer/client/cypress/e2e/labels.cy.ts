describe("Scenario labels", () => {
    const seed = "process";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.mockWindowDate();
    });

    afterEach(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    describe("designer", () => {
        it("should allow to set labels for new process", () => {
            cy.visitNewProcess(seed).as("processName");

            cy.intercept("PUT", "/api/processes/*").as("save");

            cy.intercept("POST", "/api/scenarioLabels/validation").as("labelvalidation");

            cy.get("[data-testid=AddLabel]").should("be.visible").click();

            cy.get("[data-testid=LabelInput]").as("labelInput");

            cy.get("@labelInput").should("be.visible").click().type("tagX");

            cy.wait("@labelvalidation");

            cy.get('.MuiAutocomplete-popper li[data-option-index="0"]').contains('Add label "tagX"').click();

            cy.get("[data-testid=scenario-label-0]").should("be.visible").contains("tagX");

            cy.get("@labelInput").should("be.visible").click().type("tag2");

            cy.wait("@labelvalidation");

            cy.get('.MuiAutocomplete-popper li[data-option-index="0"]').contains('Add label "tag2"').click();

            cy.get("@labelInput").type("{enter}");

            cy.get("[data-testid=scenario-label-1]").should("be.visible").contains("tag2");

            cy.contains(/^save/i).should("be.enabled").click();
            cy.contains(/^ok$/i).should("be.enabled").click();
            cy.wait("@save").its("response.statusCode").should("eq", 200);

            cy.viewport(1500, 800);

            cy.get("[data-testid=scenario-label-0]").should("be.visible").contains("tag2");
            cy.get("[data-testid=scenario-label-1]").should("be.visible").contains("tagX");

            cy.get("@labelInput").should("be.visible").click().type("very long tag");

            cy.wait("@labelvalidation").then((_) => cy.wait(100));

            cy.get("@labelInput").should("be.visible").contains("Incorrect value 'very long tag'");

            cy.contains(/^save/i).should("be.disabled");
        });

        it("should show labels for scenario", () => {
            cy.visitNewProcess(seed).then((processName) => cy.addLabelsToNewProcess(processName, ["tag1", "tag3"]));

            cy.viewport(1500, 800);

            cy.get("[data-testid=scenario-label-0]").should("be.visible").contains("tag1");
            cy.get("[data-testid=scenario-label-1]").should("be.visible").contains("tag3");
        });
    });

    describe("scenario list", () => {
        it("should allow to filter scenarios by label", () => {
            cy.visitNewProcess(seed).then((processName) => cy.addLabelsToNewProcess(processName, ["tag1", "tag3"]));
            cy.visitNewProcess(seed).then((processName) => cy.addLabelsToNewProcess(processName, ["tag2", "tag3"]));
            cy.visitNewProcess(seed).then((processName) => cy.addLabelsToNewProcess(processName, ["tag4"]));
            cy.visitNewProcess(seed);

            cy.visit("/");

            cy.contains("button", /label/i).click();

            cy.get("ul[role='menu']").within(() => {
                cy.contains(/tag2/i).click();
            });

            cy.contains(/1 of the 4 rows match the filters/i).should("be.visible");
        });
    });
});
