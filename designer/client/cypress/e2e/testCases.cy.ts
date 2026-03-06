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
        cy.openNodeDetailsTestingTab();
        appendFromLiveDataClick();
        cy.applyNodeChanges();

        cy.openNodeWindow("Enricher");
        cy.openNodeDetailsTestingTab();
        addEmptyAssertion();
        addEmptyAssertion();
        addEmptyAssertion();
        fillAssertion(0, "#records[0].input.value", "0");
        fillAssertion(1, "#records.size", "10");
        fillAssertion(2, "#records.size", "11", "!=");
        cy.applyNodeChanges();
        cy.contains(/^save$/i).click();
        cy.contains(/^ok$/i).click();
        cy.reload();
        cy.runCurrentTestCase();

        cy.openNodeWindow("Enricher");
        cy.openNodeDetailsTestingTab();
        checkAssertionResult(0, "Expected: [100] but found [0]");
        checkAssertionResult(1, "ok");
        checkAssertionResult(2, "ok");
        addEmptyAssertion();
        fillAssertion(3, "#wrongExpected", "10");
        checkAssertionErrorVisible(3, "expected", "Unresolved reference 'wrongExpected'");
        addEmptyAssertion();
        fillAssertion(4, "10", "#wrongActual");
        checkAssertionErrorVisible(4, "actual", "Unresolved reference 'wrongActual'");
    });

    it("should verify test case errors in a diagram", () => {
        cy.visitNewProcess(seed, "testCasesWithError", "Category2");
        cy.toggleUserFlag("node.showTestingTab", true);
        cy.toggleUserFlag("node.showMockFieldOnEnrichers", true);
        cy.layoutScenario();

        cy.openNodeWindow("Enricher");
        cy.openNodeDetailsTestingTab();
        addEmptyAssertion();
        fillAssertion(0, "10", "#wrongActual");
        cy.applyNodeChanges();

        cy.getNode("Event Generator").parent().as("graph");
        cy.get('[data-testid="tips-panel"]').should("exist").as("tipsToolbar");
        cy.get("@tipsToolbar").matchImage();
        cy.get("@graph").matchImage({ screenshotConfig: { padding: 16 } });
    });

    it("should verify assertion result on diagram", () => {
        cy.visitNewProcess(seed, "testCases", "Category2");
        cy.toggleUserFlag("node.showTestingTab", true);
        cy.toggleUserFlag("node.showMockFieldOnEnrichers", true);
        cy.layoutScenario();

        cy.openNodeWindow("Event Generator");
        cy.openNodeDetailsTestingTab();
        appendFromLiveDataClick();
        addEmptyAssertion();
        fillAssertion(0, "#records.size", "10");
        cy.applyNodeChanges();

        cy.openNodeWindow("Enricher");
        cy.openNodeDetailsTestingTab();
        addEmptyAssertion();
        fillAssertion(0, "10", "2");
        cy.applyNodeChanges();

        cy.runCurrentTestCase();

        cy.getNode("Event Generator").parent().as("graph");
        cy.get("@graph").matchImage({ screenshotConfig: { padding: 16 } });
    });

    it("should set generated value to enricher mock field", () => {
        cy.visitNewProcess(seed, "testCasesWithEnricherMock", "Category2");
        cy.toggleUserFlag("node.showTestingTab", true);
        cy.toggleUserFlag("node.showMockFieldOnEnrichers", true);
        cy.toggleUserFlag("editor.showResetToDefaultButton", true);
        cy.layoutScenario();

        cy.openNodeWindow("Unionreturnobjectservice");
        cy.openNodeDetailsTestingTab();
        applyGeneratedMockData();
        verifyMockData('{\n  "foo": 0\n}');
        fillMockData('{"foo": 2}');
        applyGeneratedMockData();
        verifyMockData('{\n  "foo": 0\n}');
    });

    it("should display assertions panel", () => {
        cy.visitNewProcess(seed, "testCasesWithAssertions.json", "Category2");
        cy.toggleUserFlag("node.showTestingTab", true);
        cy.layoutScenario();

        cy.runCurrentTestCase();

        expandAssertionItem("Log");

        cy.get('[data-testid="assertion-results-panel"]').matchImage();

        showAssertionDetails(0);

        cy.get('[role="tooltip"]').matchImage({ screenshotConfig: { padding: 16 } });

        openTestingDetails("Log");

        cy.location("search", { timeout: 5000 }).should("match", /activeTab=testing&nodeId=Log/i);
    });
});

const addEmptyAssertion = () => {
    cy.get('[id="Assertions-content"]').within(() => {
        cy.get('button[title="Add field"]').click();
    });
};

const fillAssertion = (assertionNumber: number, expected: string, actual: string, operator?: "==" | "!=") => {
    if (operator) {
        cy.get(`[data-testid="assertion-operator-${assertionNumber}"]`).click();
        cy.get("[id*='option-']").should("exist").contains(operator).click({ force: true });
    }

    cy.get(`[data-testid="assertion-expected-${assertionNumber}"]`).find("textarea").type(expected, { force: true }).click({ force: true });
    cy.get(`[data-testid="assertion-actual-${assertionNumber}"]`).find("textarea").type(actual, { force: true }).click({ force: true });
};

const appendFromLiveDataClick = () => {
    cy.get("[data-testid=window]")
        .contains("button", /Append from live data/i)
        .click();
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

const applyGeneratedMockData = () => {
    cy.get("[data-testid='resetToDefaultButton']").click();
    cy.get("[data-testid='applyDefaultValue']").click();
};

const fillMockData = (mockValue: string) => {
    cy.get('[id="mock-content"]').within(() => {
        cy.get(".ace_editor").should("exist");
        cy.window().then((win) => {
            const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[0]);
            aceEditor.setValue(mockValue);
            aceEditor.clearSelection();
        });
    });
};

const verifyMockData = (mockValue: string) => {
    cy.get('[id="mock-content"]').within(() => {
        cy.window().then((win) => {
            const aceEditor = (win as any).ace.edit(Cypress.$(".ace_editor")[0]);
            expect(aceEditor.getValue()).to.eq(mockValue);
        });
    });
};

const expandAssertionItem = (nodeId: string) => {
    cy.get(`[id="${nodeId}-header"]`).click();
};

const openTestingDetails = (nodeId: string) => {
    cy.get(`[id="${nodeId}-header"]`).find('[data-testid="OpenInNewIcon"]').click();
};

const showAssertionDetails = (assertionNumber: number) => {
    cy.get('[data-testid="assertionResult"').eq(assertionNumber).click();
    cy.get('[role="tooltip"]').should("be.visible");
};
