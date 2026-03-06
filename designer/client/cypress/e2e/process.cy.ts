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

    describe("initially clean", () => {
        beforeEach(() => {
            cy.visitNewProcess(seed).as("processName");
        });

        it("should have properties window", () => {
            function openWindow() {
                cy.contains(/^properties/i)
                    .should("be.enabled")
                    .click();
                cy.get("[data-testid=window]").should("be.visible");
                cy.matchQuery("?tool=properties");
            }

            openWindow();
            cy.applyNodeChanges();
            cy.get("[data-testid=window]").should("not.exist");
            cy.matchQuery("");

            openWindow();
            cy.get("[data-testid=window]")
                .contains(/^cancel/i)
                .should("be.enabled")
                .click();
            cy.get("[data-testid=window]").should("not.exist");
            cy.matchQuery("");

            openWindow();
            cy.reload();
            cy.get("[data-testid=window]").should("be.visible");
        });

        it("should allow rename", () => {
            cy.intercept("PUT", "/api/processes/*").as("save");

            cy.contains(/^properties/i)
                .should("be.enabled")
                .click();
            cy.get("[data-testid=window]").should("be.visible").find("input").first().click().type("-renamed");
            cy.applyNodeChanges();

            cy.contains(/^save/i).should("be.enabled").click();
            cy.contains(/^ok$/i).should("be.enabled").click();
            cy.wait("@save").its("response.statusCode").should("eq", 200);
            cy.get('[role="alert"]')
                .contains(/scenario name changed/i)
                .should("be.visible");
            cy.contains(/^ok$/i).should("not.exist");
            cy.location("href").should("contain", "-renamed");
        });

        it("should allow rename with other changes", () => {
            cy.intercept("PUT", "/api/processes/*").as("save");

            cy.contains(/^properties/i)
                .should("be.enabled")
                .click();
            cy.get("[data-testid=window]").should("be.visible");
            cy.get("[data-testid=window]").find('[title="Name"]').siblings().first().click().type("-renamed");
            cy.get("[data-testid=window]").find('[title="Description"]').siblings().first().type("RENAMED");
            cy.applyNodeChanges();

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

        it("should allow archive with redirect to list", function () {
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
            cy.applyNodeChanges();

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
            cy.get("[aria-label=import]")
                .next("[type=file]")
                .should("exist")
                .selectFile("cypress/fixtures/testProcess.json", { force: true });
            cy.wait("@import").its("response.statusCode").should("eq", 200);

            cy.contains(/^save/i).should("be.enabled").click();
            cy.contains(/^ok$/i).should("be.enabled").click();
            cy.wait("@save").its("response.statusCode").should("eq", 200);
            cy.contains(/^ok$/i).should("not.exist");

            cy.contains(/^counts/i).scrollIntoView();
            cy.get("#nk-graph-main").matchImage();
        });
    });

    describe("with data", () => {
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
            cy.openNodeWindow("dynamicService").contains("Dynamicservice").should("be.visible");
            cy.get("[data-testid=window]").should("be.visible").matchImage();
            cy.get("[data-testid=window]")
                .contains(/^cancel$/i)
                .click();
            cy.openNodeWindow("boundedSource").contains("Boundedsource").should("be.visible");
            cy.get("[data-testid=window]").should("be.visible").matchImage();
            cy.get("[data-testid=window]")
                .contains(/^cancel$/i)
                .click();
            cy.openNodeWindow("sendSms").contains("Sendsms").should("be.visible");
            cy.get("[data-testid=window]").should("be.visible").matchImage();
        });
    });

    it("should preserve condition on link move (switch)", () => {
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

        cy.getNode("dead-end(true)").click().type("{backspace}");
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
            cy.get("#nk-graph-main").type("{ctrl}a");
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

        cy.toggleUserFlag("node.showTestingTab", true);
        cy.openNodeWindow("request");
        cy.openNodeDetailsTestingTab();

        cy.addTestRecord();

        cy.applyNodeChanges();

        cy.runCurrentTestCase();

        cy.toggleUserFlag("node.showTestingTab", false);

        cy.openNodeWindow("request");

        cy.get("[data-testid=window]")
            .contains(/^Test case/i)
            .should("be.visible");
        cy.get("[data-testid=window]").then(($win) => {
            const width = $win.width();
            const height = $win.height();

            // maximize (one way)
            cy.wrap($win)
                .contains(/^source$/i)
                .trigger("dblclick", 1, 1);
            // restore (second way)
            cy.wait(500);
            cy.wrap($win).get("button[name=zoom]").click();

            cy.wrap($win).should(($current) => {
                expect($current.width()).to.equal(width);
                expect($current.height()).to.equal(height);
            });
        });
        cy.get("[data-testid=window]").matchImage();
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
