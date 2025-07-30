describe("Process", () => {
    const seed = "process";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.mockWindowDate();
    });

    it.only("should preserve condition on link move (switch)", () => {
        cy.intercept("POST", "/api/*Validation/*", (req) => {
            if (req.body.scenarioGraph.edges.length == 3) {
                req.alias = "validation";
            }
        });
        cy.visitNewProcess(seed, "switch");
        cy.viewport(1500, 800);
        cy.layoutScenario();

        cy.getNode("switch")
            .click()
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });

        cy.contains(/^sinks$/)
            .should("exist")
            .scrollIntoView();
        const x = 900;
        const y = 630;
        cy.get("[data-testid='component:Dead End']").should("be.visible").drag("#nk-graph-main", {
            target: {
                x,
                y,
            },
            force: true,
        });

        cy.get(`[model-id$="false"] [end="target"].marker-arrowhead`).trigger("mousedown");
        cy.get("#nk-graph-main")
            .trigger("mousemove", x, y, {
                clientX: x,
                clientY: y,
                moveThreshold: 5,
            })
            .trigger("mouseup", { force: true });

        cy.wait("@validation");
        cy.wait(500);

        cy.getNode("switch")
            .click()
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });

        cy.get(`[model-id$="false"] .label`).dblclick();
        cy.get("[data-testid=window]").should("be.visible");
        cy.contains(/^Conditions:$/)
            .parent()
            .matchImage({ screenshotConfig: { padding: 8 } });
    });

    it("should preserve condition on link move (filter)", () => {
        cy.intercept("POST", "/api/*Validation/*", (req) => {
            if (req.body.scenarioGraph.edges.length == 2) {
                req.alias = "validation";
            }
        });
        cy.visitNewProcess(seed, "filter");
        cy.viewport(1500, 800);
        cy.layoutScenario();

        cy.get(`[model-id="dead-end(true)"]`).click().type("{backspace}");
        cy.wait("@validation");

        cy.getNode("filter")
            .click()
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });

        cy.contains(/^sinks$/)
            .should("exist")
            .scrollIntoView();
        const x = 700;
        const y = 600;
        cy.get("[data-testid='component:Dead End']").should("be.visible").drag("#nk-graph-main", {
            target: {
                x,
                y,
            },
            force: true,
        });

        cy.get(`[model-id$="false"] [end="target"].marker-arrowhead`).trigger("mousedown");
        cy.get("#nk-graph-main")
            .trigger("mousemove", x, y, {
                clientX: x,
                clientY: y,
                moveThreshold: 5,
            })
            .trigger("mouseup", { force: true });

        cy.wait("@validation");
        cy.wait(500);

        cy.getNode("filter")
            .click()
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });

    it("should validate process on nodes paste", () => {
        cy.visitNewProcess(seed, "filter");
        cy.viewport(1500, 800);
        cy.layoutScenario();

        const pasteNewNodeToScenario = () => {
            cy.contains("svg", /filter/i).click();
            cy.contains("button", "copy").click();
            cy.contains("button", "paste").click();
            cy.contains("Loose node: filter (copy 1)").should("be.visible");
        };

        const copyAndPasteWholeScenario = () => {
            cy.realPress(["Meta", "A"]);
            cy.contains("button", "copy").click();
            cy.contains("button", "delete").click();
            cy.contains("Loose node: filter (copy 1)").should("not.exist");
            cy.contains("button", "paste").click();
            cy.contains("Loose node: filter (copy 1)").should("be.visible");
        };

        pasteNewNodeToScenario();
        copyAndPasteWholeScenario();

        // Center diagram before the screen to have all nodes visible
        cy.contains("button", "layout").click();
        cy.wait(500);
        cy.getNode("filter")
            .click()
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });

    it("should zoom/restore node window with test data", () => {
        cy.visitNewProcess(seed, "rrEmpty", "RequestResponse");
        cy.viewport(1500, 800);
        cy.layoutScenario();

        cy.contains("button", "Test").should("be.enabled").click();
        cy.get("[data-testid=window]").should("be.visible").find("#ace-editor").type("10");
        cy.get("[data-testid=window]")
            .contains(/^test$/i)
            .should("be.enabled")
            .click();
        cy.getNode("request").dblclick();

        cy.get("[data-testid=window]").matchImage();
        cy.get("[data-testid=window]")
            .should("contain.text", "Test case")
            .then(($win) => {
                const width = $win.width();
                const height = $win.height();

                // maximize (one way)
                cy.wrap($win)
                    .contains(/^source$/i)
                    .dblclick();
                // restore (second way)
                cy.wrap($win).get("button[name=zoom]").click();

                cy.wrap($win).should(($current) => {
                    expect($current.width()).to.equal(width);
                    expect($current.height()).to.equal(height);
                });
            });
    });

    it("should open more scenario details", () => {
        cy.visitNewProcess(seed, "rrEmpty", "RequestResponse");
        cy.contains(/^More details$/i).click();
        cy.get("[data-testid=window]")
            .should("be.visible")
            .within(() => {
                cy.contains(/^last modified$/i).should("be.visible");
            })
            .matchImage({
                maxDiffThreshold: 0.02,
            });
    });
});
