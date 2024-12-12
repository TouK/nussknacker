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
        cy.contains(/^Creator panel.*sources/i).as("toolbar");
    });

    it("should allow collapse (persist) and filtering", () => {
        cy.contains(/^sources$/i).click();
        cy.contains(/^base$/i).click();
        cy.contains(/^custom$/i).click();
        cy.contains(/^enrichers$/i).click();
        cy.contains(/^types$/i).click();
        cy.contains(/^services$/i).click();
        cy.contains(/^sinks$/i).click();
        cy.contains(/^sticky notes$/i).click();
        cy.reload();
        cy.get("@toolbar").matchImage();
        cy.get("@toolbar").find("input").type("var");
        cy.get("@toolbar").matchImage();
    });
});
