describe("Fragment", () => {
    const seed = "fragment";
    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.viewport(1440, 1200);
    });

    it.only("should allow adding input parameters and display used fragment graph in modal", () => {
        cy.visitNewFragment(seed, "fragment").as("fragmentName");
        cy.get("[model-id=input]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").should("be.visible").as("window");
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:3']").find(".fieldName input").type("xxxx");
        cy.get("[data-testid='draggable:3'] [role='button']").dndTo("[data-testid='draggable:0']");
        cy.get("[data-testid='fieldsRow:0']").find(".fieldName input").should("have.value", "xxxx");
        cy.get("@window").matchImage();
        cy.get("@window")
            .contains(/^apply$/i)
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.visitNewProcess(seed, "testProcess");
        cy.layoutScenario();

        cy.contains(/^fragments$/)
            .should("be.visible")
            .click();
        cy.contains("fragment-test")
            .last()
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: { x: 800, y: 600 },
                force: true,
            });
        cy.layoutScenario();

        cy.get("[model-id^=e2e][model-id$=fragment-test-process]").should("be.visible").trigger("dblclick");
        cy.get("#nk-graph-fragment [model-id='input']").should("be.visible");
        cy.wait(750);
        cy.get("[data-testid=window]").matchImage();

        cy.get("[data-testid=window]").find("input[value=testOutput]").type("{selectall}fragmentResult");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        cy.wait(750);
        cy.getNode("sendSms")
            .parent()
            .matchImage({
                screenshotConfig: {
                    padding: 16,
                    blackout: ["> :not(#nk-graph-main) > div"],
                },
            });

        cy.get("[model-id=sendSms]").should("be.visible").trigger("dblclick");
        cy.intercept("POST", "/api/nodes/*/validation", (request) => {
            if (request.body.nodeData.ref?.parameters[0]?.expression.expression == "#fragmentResult.") {
                request.alias = "validation";
            }
        });
        cy.intercept("POST", "/api/parameters/*/suggestions", (request) => {
            if (request?.body.expression.expression == "#fragmentResult.") {
                request.alias = "suggestions";
            }
        });
        cy.get(".ace_editor").should("be.visible").type("{selectall}#fragmentResult.");
        // We wait for validation result to be sure that red message below the form field will be visible
        cy.wait("@validation").its("response.statusCode").should("eq", 200);
        cy.wait("@suggestions").its("response.statusCode").should("eq", 200);
        cy.get(".ace_autocomplete").should("be.visible");
        cy.get("[data-testid=window]").matchImage({ maxDiffThreshold: 0.01 });

        // Save scenario
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        // Go back to the fragment
        cy.go(-1);

        // Provide new parameter to the fragment input
        cy.get("[model-id=input]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").should("be.visible").as("window");
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:4']").find(".fieldName input").type("test5");
        cy.get("@window")
            .contains(/^apply$/i)
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        // Go back to the Scenario
        cy.go(1);

        // Verify existing fragment after properties change
        cy.get("[model-id^=e2e][model-id$=fragment-test-process]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").contains("xxxx").parent().find(".ace_editor").type("test");
        cy.get("[data-testid=window]").matchImage({ maxDiffThreshold: 0.01 });
    });

    it("should open properties", () => {
        cy.visitNewFragment(seed, "fragment").as("fragmentName");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.contains(/^apply/i).should("be.enabled");
        cy.get("[data-testid=window]").matchImage({
            maxDiffThreshold: 0.01,
        });
    });

    it("should add documentation url in fragment properties and show it in modal within scenario", () => {
        const seed2 = "fragment2";
        cy.visitNewFragment(seed2, "fragment").as("fragmentName");
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();

        const docsUrl = "https://nussknacker.io/";

        cy.get("[data-testid=window]").should("be.visible").find("input").eq(1).click().type(docsUrl);

        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.contains(/^save/i).should("be.enabled").click();
        cy.intercept("PUT", "/api/processes/*").as("save");
        cy.contains(/^ok$/i).should("be.enabled").click();

        cy.wait(["@save", "@fetch"], { timeout: 20000 }).each((res) => {
            cy.wrap(res).its("response.statusCode").should("eq", 200);
        });
        cy.contains(/^ok$/i).should("not.exist");

        cy.visitNewProcess(seed, "testProcess");
        cy.layoutScenario();

        cy.contains("fragments").should("be.visible").click();
        cy.contains(`${seed2}-test`)
            .last()
            .should("be.visible")
            .drag("#nk-graph-main", { target: { x: 800, y: 600 }, force: true });
        cy.layoutScenario();

        cy.get(`[model-id^=e2e][model-id$=-${seed2}-test-process]`).should("be.visible").trigger("dblclick");

        cy.get("[title='Documentation']").should("have.attr", "href", docsUrl);
        cy.get("[data-testid=window]").as("window");
        cy.get("@window")
            .contains(/^input$/)
            .should("be.visible");
        cy.get("@window").wait(200).matchImage();

        cy.deleteAllTestProcesses({ filter: seed2 });
    });

    it("should display dead-ended fragment correct", () => {
        const fragmentName = "fragmentOutput";
        const deadEndFragmentName = "fragmentDeadEnd";
        cy.createTestFragment(fragmentName, "fragment").as("fragmentName");
        cy.visitNewProcess(seed, "testProcess").as("scenarioName");
        cy.createTestFragment(deadEndFragmentName, "deadEndFragment");
        cy.layoutScenario();

        cy.contains("fragments").should("be.visible").click();
        cy.getNode("enricher").as("enricher");
        cy.contains(`${fragmentName}-test`)
            .last()
            .should("be.visible")
            .drag("@enricher", { target: { x: 250, y: -20 }, force: true });
        cy.layoutScenario();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.get<string>("@fragmentName").then((name) => cy.visitProcess(name));
        cy.contains("sinks").should("be.visible").click();
        cy.getNode("output").as("output");
        cy.contains("dead-end")
            .first()
            .should("be.visible")
            .drag("@output", { target: { x: 0, y: 0 }, force: true });
        cy.get("@output").click().type("{backspace}");
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();
        cy.contains(/^save$/i).should("be.disabled");

        cy.viewport(2000, 800);
        cy.get<string>("@scenarioName").then((name) => cy.visitProcess(name));
        cy.getNode("sendSms").as("sendSms");
        cy.contains(`${deadEndFragmentName}-test`)
            .last()
            .should("be.visible")
            .drag("@sendSms", { target: { x: 250, y: -20 }, force: true });
        cy.layoutScenario();

        cy.get("@sendSms")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });
});
