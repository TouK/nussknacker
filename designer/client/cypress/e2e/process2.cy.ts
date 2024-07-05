describe("Process view", () => {
    const seed = "process";

    before(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    after(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "testProcess");
    });

    it("should have node search toolbar", () => {
        cy.get("[data-testid=search-panel]").should("be.visible");
        cy.get("[data-testid=search-panel]")
            .contains(/^search$/i)
            .click();
        cy.get("[title='toggle left panel']").click();
        cy.get("[data-testid=search-panel]").should("be.not.visible");
        cy.get("#nk-graph-main").click();
        cy.realPress(["Meta", "F"]);
        cy.get("[data-testid=search-panel] input").should("be.visible").should("be.focused").wait(
            500, //safety wait for animation finish
        );
        cy.realType("en");
        cy.get("[data-testid=search-panel]").contains(/sms/i).click();
        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
        cy.get("[data-testid=search-panel]")
            .scrollIntoView()
            .matchImage({ diffConfig: { threshold: 0.08 } });
        cy.get("[data-testid=search-panel]")
            .contains(/source/i)
            .click()
            .click();
        cy.get("[data-testid=window]")
            .contains(/^source$/i)
            .should("be.visible");
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.get("#nk-graph-main").click();
        cy.realPress(["Meta", "F"]);
        cy.get("[data-testid=search-panel] input").should("be.visible").should("be.focused");
        cy.realType("source");
        cy.wait(750); //wait for animation
        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });

    it("should display description", () => {
        cy.get(`[title="toggle description view"]`).should("not.exist");

        cy.contains(/^properties$/i)
            .should("be.visible")
            .dblclick();
        cy.get("[data-testid=window]").should("be.visible").as("window");

        cy.contains("Description:")
            .next()
            .find("textarea")
            .should("be.visible")
            .click("center")
            .type("# description header{enter}")
            .type("{enter}")
            .type("description paragraph");

        cy.get("@window")
            .contains(/^apply$/i)
            .click();

        cy.get(`[title="toggle description view"]`).should("be.visible").click().should("not.exist");

        cy.contains("description header").should("be.visible");
        cy.contains("description paragraph").should("be.visible").parent().parent().as("description");

        cy.viewport(1200, 600);
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.viewport(1450, 600);
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.get("[title='toggle right panel']").click();
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.get("[title='toggle left panel']").click();
        cy.get("@description").matchImage({ screenshotConfig: { padding: [20, 100] } });

        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.reload();
        cy.get(`[title="toggle description view"]`).should("be.visible");
    });
});
