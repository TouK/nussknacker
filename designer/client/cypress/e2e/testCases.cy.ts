const seed = "testCases";

describe("Test cases", () => {
    before(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("should verify test assertions", () => {
        cy.visitNewProcess(seed, "testCases", "Category2");
        cy.toggleUserFlag("node.showTestingTab", true);
        cy.toggleUserFlag("node.showMockFieldOnEnrichers", true);
        cy.layoutScenario();

        cy.openNodeWindow("Event Generator");
        openTestingTab();
        appendFromLiveDataClick();
        cy.applyNodeChanges();

        cy.openNodeWindow("Enricher");
        openTestingTab();
        addEmptyAssertion();
        addEmptyAssertion();
        fillAssertion(0, "#contexts[0].input.value", "0");
        fillAssertion(1, "#contexts.size", "10");
        cy.applyNodeChanges();
        cy.contains(/^save$/i).click();
        cy.contains(/^ok$/i).click();
        cy.reload();
        rerunTest();

        cy.openNodeWindow("Enricher");
        openTestingTab();
        checkAssertionResult(0, "Expected: [100] but found [0]");
        checkAssertionResult(1, "ok");
        addEmptyAssertion();
        fillAssertion(2, "#wrongExpected", "10");
        checkAssertionErrorVisible(2, "expected", "Unresolved reference 'wrongExpected'");
        addEmptyAssertion();
        fillAssertion(3, "10", "#wrongActual");
        checkAssertionErrorVisible(3, "actual", "Unresolved reference 'wrongActual'");
    });
});

const openTestingTab = () => {
    cy.get('[role="tab"]')
        .contains(/testing/i)
        .click();
};

const addEmptyAssertion = () => {
    cy.get('[id="Assertions-content"]').within(() => {
        cy.get('button[title="Add field"]').click();
    });
};

const fillAssertion = (assertionNumber: number, expected: string, actual: string) => {
    cy.get(`[data-testid="assertion-expected-${assertionNumber}"]`).find("textarea").type(expected, { force: true });
    cy.get(`[data-testid="assertion-actual-${assertionNumber}"]`).find("textarea").type(actual, { force: true });
};

const appendFromLiveDataClick = () => {
    cy.get("[data-testid=window]")
        .contains("button", /Append from live data/i)
        .click();
};

const rerunTest = () => {
    cy.intercept("POST", "/api/processManagement/testCase/*").as("retest");
    cy.contains('[data-testid="toolbarButton-label"]', /Rerun test/).click();
    cy.wait("@retest");
};

const checkAssertionResult = (assertionNumber: number, message: string) => {
    cy.get(`[data-testid="fieldsRow:${assertionNumber}"]`).siblings().eq(0).realHover();
    cy.get('[role="tooltip"]').should("be.visible").should("contain.text", message);
    cy.get('[id="Assertions-content"]').realHover();
    cy.get('[role="tooltip"]').should("not.exist");
};

const checkAssertionErrorVisible = (assertionNumber: number, fieldType: "actual" | "expected", message: string) => {
    const notSelectedFieldType = fieldType === "actual" ? "expected" : "actual";

    cy.get(`[data-testid="assertion-${fieldType}-${assertionNumber}"]`).within(() => {
        cy.contains('[data-testid="form-helper-text"]', message).should("be.visible");
    });
    cy.get(`[data-testid="assertion-${notSelectedFieldType}-${assertionNumber}"]`).within(() => {
        cy.contains('[data-testid="form-helper-text"]', message).should("not.exist");
    });
};
