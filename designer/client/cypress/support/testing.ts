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
    cy.contains('[data-testid="window"] button', /^add record$/i)
        .should("be.visible")
        .click({ force: true });
    cy.get('[data-testid="data-records-table-container"]').should("be.visible");
    if (callback) {
        callback();
    }
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
