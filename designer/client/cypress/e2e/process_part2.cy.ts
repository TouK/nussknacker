describe("Process with data", () => {
    const seed = "process";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    const screenshotOptions: Cypress.MatchImageOptions = {
        screenshotConfig: {
            blackout: ["[data-testid=SidePanel]"],
        },
    };

    beforeEach(() => {
        cy.mockWindowDate();
        cy.visitNewProcess(seed, "testProcess");
    });

    it("should allow drag node", () => {
        cy.get("[title='toggle left panel']").click();
        cy.layoutScenario();
        cy.dragNode("dynamicService", { x: 150, y: 150 });
        cy.get("[data-testid=graphPage]").matchImage(screenshotOptions);
    });

    it("should allow drag component and drop on edge", () => {
        cy.contains(/^custom$/)
            .should("exist")
            .scrollIntoView();
        cy.layoutScenario();
        cy.get("[data-testid='component:Customfilter']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 580,
                    y: 450,
                },
                force: true,
            });
        cy.get("[data-testid=graphPage]").matchImage(screenshotOptions);
        //why save and test snapshot? mistake?
        cy.verifySaveIndicator();
        cy.contains(/^save$/i).click();
        cy.get("[data-testid=window]").contains(/^ok$/i).click();
        cy.get("[data-testid=window]").should("not.exist");
        cy.get("#nk-graph-main").should("be.visible");
        cy.get("[data-testid=graphPage]").matchImage(screenshotOptions);
    });

    it("should return 400 status code and show info about required comment", () => {
        cy.viewport("macbook-15");
        cy.contains(/^deploy$/i).click();
        cy.intercept("POST", "/api/processManagement/deploy/*").as("deploy");
        cy.contains(/^ok$/i).should("be.enabled").as("okButton");
        cy.get("@okButton").click();
        cy.wait("@deploy", { timeout: 20000 }).its("response.statusCode").should("eq", 400);
        cy.contains(/^Comment is required.$/i).should("exist");
    });

    // This test is for  deploy scenario dialog snapshot comparing only (equal snapshot).
    // For some reason cypress does not have a valid snapshot comparison inside another test case.
    it("should make a deploy of the new version", () => {
        cy.viewport("macbook-15");

        cy.deployScenario(undefined, true);
    });

    it("should display some node details in modal", () => {
        cy.get("[model-id=dynamicService]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").contains("Dynamicservice").should("be.visible");
        cy.get("[data-testid=window]").should("be.visible").matchImage();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.get("[model-id=boundedSource]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").contains("Boundedsource").should("be.visible");
        cy.get("[data-testid=window]").should("be.visible").matchImage();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.get("[model-id=sendSms]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").contains("Sendsms").should("be.visible");
        cy.get("[data-testid=window]").should("be.visible").matchImage();
    });
});
