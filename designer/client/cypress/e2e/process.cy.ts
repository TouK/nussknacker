describe("Process initially clean", () => {
    const seed = "process";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.mockWindowDate();
        cy.visitNewProcess(seed).as("processName");
    });

    it.only("should allow rename", () => {
        cy.intercept("PUT", "/api/processes/*").as("save");

        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible").find("input").first().click().type("-renamed");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        cy.contains(/^save/i).should("be.enabled").click();
        cy.contains(/^ok$/i).should("be.enabled").click();
        cy.wait("@save").its("response.statusCode").should("eq", 200);
        cy.get('[role="alert"]')
            .contains(/scenario name changed/i)
            .should("be.visible");
        cy.contains(/^ok$/i).should("not.exist");
        cy.location("href").should("contain", "-renamed");
    });

    it.only("should allow rename with other changes", () => {
        cy.intercept("PUT", "/api/processes/*").as("save");

        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible");
        cy.get("[data-testid=window]").find('[title="Name"]').siblings().first().click().type("-renamed");
        cy.get("[data-testid=window]").find('[title="Description"]').siblings().first().type("RENAMED");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        cy.contains(/^save/i).should("be.enabled").click();
        cy.contains(/^ok$/i).should("be.enabled").click();
        cy.wait("@save").its("response.statusCode").should("eq", 200);
        cy.get('[role="alert"]')
            .contains(/scenario name changed/i)
            .should("be.visible");

        cy.contains(/^ok$/i).should("not.exist");
        cy.location("href").should("contain", "-renamed");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").find('[title="Description"]').siblings().first().should("contain", "RENAMED");
    });

    it.only("should allow archive with redirect to list", function () {
        cy.contains(/^archive/i)
            .should("be.enabled")
            .click();
        cy.contains("want to archive").should("be.visible");
        cy.contains(/^yes$/i).should("be.enabled").click();
        cy.contains(/^archived$/i, { timeout: 60000 }).should("be.visible");
        cy.contains(this.processName).should("be.visible").click({ force: true });
        cy.contains(/scenario was archived/i).should("be.visible");
    });

    it("should open properties from tips panel", () => {
        cy.viewport("macbook-15");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible").find("input").as("inputs");
        cy.get("@inputs").first().click().type("-renamed");
        //this is idx of "Max events", which should be int
        cy.get("@inputs").eq(3).click().type("wrong data");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("not.exist");
        cy.contains(/^tips.*errors in/i)
            .contains(/^properties/i)
            .should("be.visible")
            .click();
        cy.get("[data-testid=window]").matchImage();
    });

    it("should import JSON and save", () => {
        cy.intercept("PUT", "/api/processes/*").as("save");
        cy.contains(/is not deployed/i).should("be.visible");
        cy.get("#nk-graph-main").matchImage();

        cy.intercept("POST", "/api/processes/import/*").as("import");
        cy.get("[aria-label=import]").next("[type=file]").should("exist").selectFile("cypress/fixtures/testProcess.json", { force: true });
        cy.wait("@import").its("response.statusCode").should("eq", 200);

        cy.contains(/^save/i).should("be.enabled").click();
        cy.contains(/^ok$/i).should("be.enabled").click();
        cy.wait("@save").its("response.statusCode").should("eq", 200);
        cy.contains(/^ok$/i).should("not.exist");

        cy.contains(/^counts/i).scrollIntoView();
        cy.get("#nk-graph-main").matchImage();
    });
});
