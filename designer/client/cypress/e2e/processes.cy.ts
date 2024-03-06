describe("Processes list", () => {
    const NAME = "process-list";

    before(() => {
        cy.deleteAllTestProcesses({ filter: NAME, force: true });
        cy.createTestProcessName(NAME).as("processName");
        cy.createTestProcess(`${NAME}-processing-mode-Request-Response`, undefined, "RequestResponse", "Request-Response").as(
            "request-response-scenario",
        );
        cy.createTestProcess(`${NAME}-processing-mode-Streaming`, undefined, undefined, "Unbounded-Stream");
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: NAME });
    });

    beforeEach(() => {
        cy.visit("/");
        cy.url().should("match", /scenarios/);
        cy.get("[placeholder='Search...']", { timeout: 60000 }).should("be.visible");
        cy.contains(/^loading.../i, { timeout: 60000 }).should("not.exist");
    });

    it("should have no process matching filter", () => {
        cy.get("[placeholder='Search...']").type(NAME);
        cy.contains(/^none of the|^the list is empty$/i).should("be.visible");
    });

    it("should allow creating new process", function () {
        cy.contains(/^add new scenario$/i)
            .should("be.visible")
            .click();
        cy.get("#newProcessName", { timeout: 30000 }).type(this.processName);
        cy.contains(/request-response/i).click();
        cy.get("#processCategory").select(2);
        cy.contains(/^create$/i)
            .should("be.enabled")
            .click();
        cy.url().should("contain", `visualization/${this.processName}`);
    });

    it("should have test process on list", function () {
        cy.get("[placeholder='Search...']").type(NAME);
        cy.url().should("contain", NAME);
        cy.contains(/^every of the|^1 of the/i).should("be.visible");
        cy.wait(200); // wait for highlight
        cy.contains(this.processName).should("be.visible"); //.matchImage() FIXME
        cy.contains(this.processName).click({ x: 10, y: 10 });
        cy.url().should("contain", `visualization/${this.processName}`);
    });

    it("should filter by processing mode", function () {
        cy.get("[placeholder='Search...']").type(`${NAME}-processing-mode`);
        cy.contains(/every of the 2 rows match the filters/i).should("be.visible");

        cy.get("[role='grid']").matchImage({ maxDiffThreshold: 0.0001 });

        cy.contains("button", /processing mode/i).click();

        cy.get("ul[role='menu']").matchImage();

        cy.get("ul[role='menu']").within(() => {
            cy.contains(/streaming/i).click();
        });

        cy.contains(/1 of the 2 rows match the filters/i).should("be.visible");

        cy.get("ul[role='menu']").within(() => {
            cy.contains(/Default/i).click();
        });

        cy.contains(/every of the 2 rows match the filters/i).should("be.visible");

        cy.get("ul[role='menu']").within(() => {
            cy.contains(/Request-Response/i).click();
        });

        cy.contains(/1 of the 2 rows match the filters/i).should("be.visible");
    });
});

describe.skip("Processes list (new table)", () => {
    const NAME = "process-list";

    before(() => {
        cy.deleteAllTestProcesses({ filter: NAME, force: true });
        cy.createTestProcessName(NAME).as("processName");
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: NAME });
    });

    beforeEach(() => {
        cy.visit("/");
        cy.url().should("match", /scenarios/);
        cy.get("[placeholder='Search...']", { timeout: 60000 }).should("be.visible");
    });

    it("should have no process matching filter", () => {
        cy.get("[placeholder='Search...']").type(NAME);
        cy.contains(/^no rows$/i).should("be.visible");
    });

    it("should allow creating new process", function () {
        cy.contains(/^add new scenario$/i)
            .should("be.visible")
            .click();
        cy.get("#newProcessName", { timeout: 30000 }).type(this.processName);
        cy.contains(/^create$/i)
            .should("be.enabled")
            .click();
        cy.url().should("contain", `visualization/${this.processName}`);
    });

    it("should have test process on list", function () {
        cy.get("[placeholder='Search...']").type(NAME);
        cy.url().should("contain", NAME);
        cy.wait(200); // wait for highlight
        cy.contains(this.processName).should("be.visible");
        cy.get("#app-container").matchImage();
        cy.contains(this.processName).click({ x: 10, y: 10 });
        cy.url().should("contain", `visualization/${this.processName}`);
    });
});
