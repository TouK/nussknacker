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

    it("should allow adding input parameters and display used fragment graph in modal", () => {
        const toggleSettings = (fieldNumber: number) => {
            cy.get(`[data-testid='fieldsRow:${fieldNumber}']`).find("[title='SettingsButton']").click();
        };

        cy.visitNewFragment(seed, "fragment").as("fragmentName");
        cy.get("[model-id=input]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").should("be.visible").as("window");

        // Provide String Any Value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:3']").find("[placeholder='Field name']").type("name_value_string_any_value");
        toggleSettings(3);
        cy.get("[data-testid='draggable:3'] [role='button']").dndTo("[data-testid='draggable:0']");
        cy.get("[data-testid='fieldsRow:0']").find("[placeholder='Field name']").should("have.value", "name_value_string_any_value");
        cy.get("@window").matchImage();

        // Provide String Any with Suggestion Value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:4']").find("[placeholder='Field name']").type("name_string_any_with_suggestion");
        toggleSettings(4);
        cy.get("[data-testid='settings:4']").contains("Any value").click();
        cy.get("[id$='option-1']").click({ force: true });

        // Display Add list item errors when blank value
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:4']").find("[type='ERROR']").should("be.visible");
        cy.get("[data-testid='settings:4']").contains("This field is mandatory and can not be empty");

        // Display Add list item errors from a backend when Non reference value occurs
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("w");
        cy.get("[data-testid='settings:4']").find("[type='ERROR']").should("be.visible");
        cy.get("[data-testid='settings:4']").contains(
            "Failed to parse expression: Non reference 'w' occurred. Maybe you missed '#' in front of it?",
        );
        cy.get("[data-testid='settings:4']").find("[role='button']").should("not.exist");

        // Display Add list item value without error
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{backspace}");
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("#meta.processName");
        cy.get("[data-testid='settings:4']").find("[type='ERROR']").should("not.exist");
        cy.get("[data-testid='settings:4']").contains("Typing...").should("not.exist");
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:4']").find("[role='button']").contains("#meta.processName");

        // Display Add list item errors when a value is not unique
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("#meta.processName");
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:4']").find("[type='ERROR']").should("be.visible");
        cy.get("[data-testid='settings:4']").contains("This field has to be unique");

        // Provide String Any with Suggestion Value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:5']").find("[placeholder='Field name']").type("name_string_fixed");
        toggleSettings(5);
        cy.get("[data-testid='settings:5']").contains("Any value").click();
        cy.get("[id$='option-0']").click({ force: true });
        cy.get("[data-testid='settings:5']").find("[id='ace-editor']").type("#meta.processName");
        cy.get("[data-testid='settings:5']").contains("Typing...").should("not.exist");
        cy.get("[data-testid='settings:5']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:5']").find("[role='button']").contains("#meta.processName");
        cy.get("[data-testid='settings:5']").find("[aria-label='type-select']").eq(1).click();
        cy.get("[id$='option-1']").click({ force: true });
        cy.get("[data-testid='settings:5']").find("textarea").eq(1).type("Hint text test");

        // Provide non String or Boolean Any Value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:6']").find("[placeholder='Field name']").type("non_boolean_or_string");
        cy.get("[data-testid='fieldsRow:6']").contains("String").click();
        cy.contains("Number").click({ force: true });
        toggleSettings(6);
        cy.get("[data-testid='settings:6']").find("[id='ace-editor']").type("1");

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

        cy.get('[title="name_string_any_with_suggestion"]').siblings().eq(0).find('[title="Switch to expression mode"]');
        cy.get('[title="name_string_fixed"]').siblings().eq(0).contains("#meta.processName");
        cy.get('[title="name_string_fixed"]').find('[title="Hint text test"]').should("be.visible");
        cy.get('[title="non_boolean_or_string"]').siblings().eq(0).contains("1");

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
        cy.get("[data-testid='fieldsRow:7']").find("[placeholder='Field name']").type("test5");
        cy.get("@window")
            .contains(/^apply$/i)
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        // Go back to the Scenario
        cy.go(1);

        // Verify existing fragment after properties change
        cy.get("[model-id^=e2e][model-id$=fragment-test-process]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").get("[title='name_value_string_any_value']").siblings().eq(0).find("[id='ace-editor']").type("test");
        cy.intercept("POST", "/api/properties/*/validation").as("validation");

        cy.wait("@validation");
        cy.get("[data-testid=window]").matchImage({ maxDiffThreshold: 0.01 });
    });

    it("should open properties", () => {
        cy.visitNewFragment(seed, "fragment").as("fragmentName");

        cy.intercept("POST", "/api/properties/*/validation").as("validation");

        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.contains(/^apply/i).should("be.enabled");
        cy.wait("@validation");
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
