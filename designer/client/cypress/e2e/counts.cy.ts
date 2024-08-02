describe("Counts", () => {
    const seed = "counts";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "counts");
    });

    it("should be available via button and modal", () => {
        cy.viewport("macbook-15");

        // Collapse toolbar to make counts button visible
        cy.contains(/^scenario details$/i).click();
        cy.contains(/^counts$/i).as("button");
        cy.get("@button").should("be.visible").matchImage();
        cy.get("@button").click();

        cy.get("[data-testid=window]").contains("Quick ranges").should("be.visible");
        cy.contains(/^latest deploy$/i).should("not.exist");
        cy.get("[data-testid=window]").matchImage();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();

        cy.deployScenario();
        cy.get("@button").click();
        cy.get("[data-testid=window]").contains("Quick ranges").should("be.visible");
        cy.contains(/^latest deploy$/i).should("be.visible");
        cy.get("[data-testid=window]").matchImage();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.cancelScenario();

        cy.deployScenario();
        cy.cancelScenario();
        cy.deployScenario();

        cy.get("@button").click();
        cy.get("[data-testid=window]").contains("Quick ranges").should("be.visible");
        cy.contains(/^previous deployments...$/i)
            .should("be.visible")
            .click();
        cy.get("[data-testid=window]").matchImage();
        cy.get("[data-testid=window]").contains("no refresh").should("be.visible");
        cy.get("[data-testid=window]").contains("Latest deploy").click();
        cy.get("[data-testid=window]").contains("10 seconds").should("be.visible");
    });

    it("should display question mark when renaming a node and updating the count", () => {
        const fakeResponse = {
            periodic: { all: 10, errors: 0, fragmentCounts: {} },
            "dead-end": { all: 120, errors: 10, fragmentCounts: {} },
        };

        cy.intercept("GET", "/api/processCounts/*", fakeResponse);

        cy.contains(/^counts$/i).click();
        cy.get("[data-testid=window]")
            .contains(/^today$/i)
            .click();
        cy.get("[data-testid=window]").contains(/^ok$/i).click();

        cy.get("[model-id=dead-end]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").find("input[type=text]").type("12");
        cy.get("[data-testid=window]")
            .contains(/^apply$/i)
            .click();

        cy.intercept("GET", "/api/processCounts/*", fakeResponse);

        cy.contains(/^counts$/i).click();

        cy.get("[data-testid=window]")
            .contains(/^today$/i)
            .click();
        cy.get("[data-testid=window]").contains(/^ok$/i).click();

        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });
});
