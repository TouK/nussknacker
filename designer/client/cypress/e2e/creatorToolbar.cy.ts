const width = 1440;
const height = 900;

describe("Creator toolbar", () => {
    const seed = "creator";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.viewport(width, height);
        cy.visitNewProcess(seed).as("processName");
        cy.contains(/^Creator panel.*sources/i, { timeout: 30000 }).as("toolbar", { type: "query" });
    });

    it("should allow collapse (persist) and filtering", () => {
        cy.contains(/^sources$/i).click();
        cy.contains(/^base$/i).click();
        cy.contains(/^custom$/i).click();
        cy.contains(/^enrichers$/i).click();
        cy.contains(/^types$/i).click();
        cy.contains(/^services$/i).click();
        cy.contains(/^sinks$/i).click();
        cy.contains(/^Misc$/i).click();
        cy.reload();
        cy.get("@toolbar").matchImage();
        cy.get("@toolbar").find("input").type("var");
        cy.get("@toolbar").matchImage();
    });

    it("should display tooltip when tool is truncated", () => {
        const componentWithTooltip = "Kafka Transaction No Test Timestamp Assigner";
        const componentWithoutTooltip = "Kafka Transaction";

        cy.get(`[data-testid="component:${componentWithTooltip}"]`).realMouseDown();
        cy.contains('[role="tooltip"]', componentWithTooltip).should("be.visible");

        cy.get(`[data-testid="component:${componentWithoutTooltip}"]`).realMouseDown();
        cy.contains('[role="tooltip"]').should("not.exist");
    });
});
