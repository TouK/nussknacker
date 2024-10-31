describe("Node window", () => {
    const NAME = "node-window";

    before(() => {
        cy.deleteAllTestProcesses({ filter: NAME, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: NAME });
    });

    beforeEach(() => {
        cy.viewport(1600, 800);
    });

    it("should display periodic source", () => {
        cy.visitNewProcess(NAME).as("processName");
        cy.contains(/^sources$/)
            .should("exist")
            .scrollIntoView();
        cy.layoutScenario();
        cy.get("[data-testid='component:periodic']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 800,
                    y: 300,
                },
                force: true,
            });

        cy.getNode("periodic").dblclick();

        // TODO: fix validation display in node windows
        cy.intercept("POST", "/api/nodes/*/validation").as("validation");
        cy.wait("@validation");

        cy.get("[data-testid=window]").should("be.visible");
        cy.contains(/^hours$/).should("be.visible");
        cy.get("[data-testid=window]").matchImage();
    });

    describe.skip("with query params", () => {
        beforeEach(() => {
            cy.visit("/components");
        });

        it("should store nodeId in query and work with history", () => {
            cy.createTestProcess(NAME, "testProcess2");
            cy.intercept("POST", "/api/nodes/*/validation").as("validation");

            cy.visit("/components/usages/builtin-filter");
            cy.contains(/^filter 1$/i)
                .should("be.visible")
                .click();

            cy.wait("@validation");
            cy.location("pathname", { timeout: 5000 }).should("match", /^\/visualization\/e2e/i);
            cy.location("search", { timeout: 5000 }).should("match", /nodeId=filter/i);
            cy.get("[data-testid=window]").should("be.visible");

            cy.go("back");

            cy.contains(/^filter 1$/i).should("be.visible");
            cy.location("pathname", { timeout: 1000 }).should("not.match", /^\/visualization\/e2e/i);
            cy.location("search", { timeout: 1000 }).should("not.match", /nodeId/i);
            cy.wait("@validation");

            cy.go("forward");

            cy.wait("@validation");
            cy.location("pathname", { timeout: 5000 }).should("match", /^\/visualization\/e2e/i);
            cy.location("search", { timeout: 5000 }).should("match", /nodeId=filter/i);
            cy.get("[data-testid=window]").should("be.visible");

            cy.get("[data-testid=window]")
                .contains(/^cancel$/i)
                .click();
            cy.getNode("switch").dblclick();
            cy.wait("@validation");
            cy.get("[data-testid=window]").should("be.visible");

            cy.go("back");

            cy.contains(/^filter 1$/i).should("be.visible");
            cy.location("pathname", { timeout: 1000 }).should("not.match", /^\/visualization\/e2e/i);
            cy.location("search", { timeout: 1000 }).should("not.match", /nodeId/i);

            cy.go("forward");

            cy.wait("@validation");
            cy.location("pathname", { timeout: 5000 }).should("match", /^\/visualization\/e2e/i);
            cy.location("search", { timeout: 5000 }).should("match", /nodeId=switch/i);
            cy.get("[data-testid=window]").should("be.visible");
        });

        it("should be visible for node in fragment", () => {
            cy.createTestFragment(`${NAME}_xxx`, "fragmentWithFilter").as("fragmentName");
            cy.visitNewProcess(`${NAME}_yyy`, "testProcess");

            // TODO: simplify, don't want to test dnd/save here
            cy.get("#toolbox").contains("fragments").should("exist").scrollIntoView();
            cy.layoutScenario();
            cy.contains(`${NAME}_xxx`)
                .last()
                .should("be.visible")
                .drag("#nk-graph-main", {
                    target: {
                        x: 880,
                        y: 550,
                    },
                    force: true,
                });
            cy.contains(/^save\*$/i).click();
            cy.contains(/^ok$/i).click();

            cy.visit("/components/usages/builtin-filter");
            cy.contains(/^filter.*xxx-test-process/i)
                .should("be.visible")
                .click();

            cy.get("[data-testid=window]")
                .should("have.lengthOf", 2)
                .eq(1)
                .find("input")
                .eq(0)
                .should((currentSubject) => {
                    expect(currentSubject.val()).to.match(/^.*xxx-test-process-filter$/i);
                });

            cy.reload();

            cy.get("[data-testid=window]")
                .should("have.lengthOf", 2)
                .eq(1)
                .find("input")
                .eq(0)
                .should((currentSubject) => {
                    expect(currentSubject.val()).to.match(/^.*xxx-test-process-filter$/i);
                });

            cy.location("pathname", { timeout: 1000 }).should("match", /^\/visualization\/e2e/i);
            cy.location("search", { timeout: 1000 }).should("match", /nodeId=.*xxx-test-process,.*xxx-test-process-filter/i);
        });
    });

    it("should not open twice on deploy", () => {
        cy.visitNewProcess(NAME, "testProcess");

        cy.deployScenario("---");
        cy.intercept("/api/notifications", (req) =>
            req.continue((res) => {
                res.setDelay(500);
            }),
        );
        cy.cancelScenario("---");
        cy.getNode("enricher").dblclick();
        cy.get("[data-testid=window]").should("be.visible");
        cy.location("search").should("match", /nodeId=enricher/i);
        cy.contains("canceled").should("be.visible");
        cy.wait(2000);
        cy.get("[data-testid=window]").its("length").should("eq", 1);
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();
        cy.get("[data-testid=window]").its("length").should("eq", 0);
    });

    it("should remove input focus and close the window on double escape click", () => {
        cy.intercept("POST", "/api/nodes/*/validation").as("inputValidation");
        cy.visitNewProcess(NAME, "testProcess");
        cy.getNode("enricher").dblclick();
        cy.wait("@inputValidation");
        cy.get("[data-testid=window]").should("be.visible");
        cy.get("body").type("{esc}");
        cy.get("body").type("{esc}");
        cy.get("[data-testid=window]").should("not.exist");
    });
});
