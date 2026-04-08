declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            addTestRecord: typeof addTestRecord;
            runCurrentTestCase: typeof runCurrentTestCase;
            runAllTestCases: typeof runAllTestCases;
        }
    }
}

const runAllTestCases = () => {
    cy.intercept("POST", "/api/scenarioTesting/*/performMultipleTestCases").as("scenarioTestAll");
    cy.get('[data-selector="SCENARIO_TEST"]').parent().find(".toolbarButton-MenuExpand").click();
    cy.contains('[role="menuitem"]', /run all/i).click();
    cy.wait("@scenarioTestAll");
};

const addTestRecord = (callback?: () => void) => {
    cy.get('[data-testid="data-grid-canvas"]').should("exist");
    cy.wait(500);
    cy.get('[data-testid="data-grid-canvas"]').then(($canvas) => {
        const rect = $canvas[0].getBoundingClientRect();
        // Click near bottom right corner
        const clickX = rect.width - 10;
        const clickY = rect.height - 10;

        cy.wrap($canvas).click(clickX, clickY, { force: true });
        if (callback) {
            callback();
        }
    });
};

const runCurrentTestCase = () => {
    cy.intercept("POST", "/api/scenarioTesting/*/performTestCase").as("scenarioTest");
    cy.get('[data-selector="SCENARIO_TEST"]').click();
    cy.wait("@scenarioTest");
};

Cypress.Commands.add("addTestRecord", addTestRecord);
Cypress.Commands.add("runCurrentTestCase", runCurrentTestCase);
Cypress.Commands.add("runAllTestCases", runAllTestCases);

export default {};
