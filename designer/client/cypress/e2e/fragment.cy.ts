describe("Fragment", () => {
    const seed = "fragment";
    before(() => {
        cy.deleteAllTestProcesses({
            filter: seed,
            force: true,
        });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.viewport(1440, 1200);
    });

    it("should allow adding input parameters and display used fragment graph in modal", () => {
        const toggleSettings = (fieldNumber: number) => {
            cy.get(`[data-testid='fieldsRow:${fieldNumber}']`).find("[title='Options']").click();
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

        cy.get("@window").find("[data-testid='settings:0']").matchImage();

        // Provide String Any with Suggestion Value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:4']").find("[placeholder='Field name']").type("name_string_any_with_suggestion");
        toggleSettings(4);
        cy.get("[data-testid='settings:4']").contains("Any value").select(1);

        // Display Add list item errors when blank value
        cy.get("[data-testid='settings:4']").contains("User defined list").click();
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:4']").find("[data-testid='form-helper-text']").should("be.visible");
        cy.get("[data-testid='settings:4']").contains("This field is mandatory and can not be empty");

        // Display Add list item errors from a backend when Non reference value occurs
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("w");
        cy.get("[data-testid='settings:4']").find("[data-testid='form-helper-text']").should("be.visible");
        cy.get("[data-testid='settings:4']").contains(
            "Failed to parse expression: Non reference 'w' occurred. Maybe you missed '#' in front of it?",
        );
        cy.get("[data-testid='settings:4']").find("[role='button']").should("not.exist");

        // Display Add list item value without error
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{backspace}");
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("#meta.processName");
        cy.get("[data-testid='settings:4']").find("[data-testid='form-helper-text']").should("not.exist");
        cy.get("[data-testid='settings:4']").contains("Typing...").should("not.exist");
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:4']").find("[role='button']").contains("#meta.processName");

        // Display Add list item errors when a value is not unique
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("#meta.processName");
        cy.get("[data-testid='settings:4']").contains("Typing...").should("not.exist");
        cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:4']")
            .contains(/Add list item/i)
            .siblings()
            .eq(0)
            .find("[data-testid='form-helper-text']")
            .contains("This field has to be unique")
            .should("be.visible");

        cy.get("@window").find("[data-testid='settings:4']").matchImage();

        // Provide String Fixed value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:5']").find("[placeholder='Field name']").type("name_string_fixed");
        toggleSettings(5);
        cy.get("[data-testid='settings:5']").contains("Any value").select(1);
        cy.get("[data-testid='settings:5']").contains("User defined list").click();
        cy.get("[data-testid='settings:5']").find("[id='ace-editor']").type("#meta.processName");
        cy.get("[data-testid='settings:5']").contains("Typing...").should("not.exist");
        cy.get("[data-testid='settings:5']").find("[id='ace-editor']").type("{enter}");
        cy.get("[data-testid='settings:5']").find("[role='button']").contains("#meta.processName");
        cy.get("[data-testid='settings:5']").find("[aria-label='type-select']").eq(1).select(1);
        cy.get("[data-testid='settings:5']").find("textarea").eq(1).type("Hint text test");

        cy.get("[data-testid='settings:5']")
            .contains(/Input mode/i)
            .siblings()
            .eq(0)
            .find("[data-testid='form-helper-text']")
            .should("not.exist");
        cy.get("@window").find("[data-testid='settings:5']").matchImage();

        // Provide non String or Boolean Any Value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:6']").find("[placeholder='Field name']").type("non_boolean_or_string");
        cy.get("[data-testid='fieldsRow:6']").contains("String").click();
        cy.contains("Number").click({ force: true });
        toggleSettings(6);
        cy.get("[data-testid='settings:6']").find("[id='ace-editor']").type("1");

        // Activate a validation
        cy.get("[data-testid='settings:6']")
            .find("label")
            .contains(/validation/i)
            .siblings()
            .find("label")
            .click();
        cy.get("[data-testid='settings:6']")
            .find("label")
            .contains(/Validation expression/i)
            .siblings()
            .eq(0)
            .find("[data-testid='form-helper-text']")
            .should("not.exist");

        cy.get("@window").find("[data-testid='settings:6']").matchImage();

        // Provide String Fixed value inputMode
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:7']").find("[placeholder='Field name']").type("any_value_with_suggestions_preset");
        toggleSettings(7);

        // Select any value with suggestions Input mode
        cy.get("[data-testid='settings:7']").contains("Any value").select(1);

        // Activate preset mode
        cy.get("[data-testid='settings:7']").contains("Preset").click();

        // Initial value should be disabled when there is no preset selected
        cy.get("[data-testid='settings:7']")
            .find("label")
            .contains(/initial value/i)
            .siblings()
            .find("input")
            .should("be.disabled");

        // Select available values from Preset
        cy.get("[data-testid='settings:7']")
            .find("label")
            .contains(/preset selection/i)
            .siblings()
            .eq(0)
            .find("input")
            .select(1);

        // Select Initial value
        cy.get("[data-testid='settings:7']")
            .find("label")
            .contains(/initial value/i)
            .siblings()
            .eq(0)
            .click();
        cy.get("[id$='option-1']").click({ force: true });

        cy.get("@window")
            .contains(/^apply$/i)
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.visitNewProcess(seed, "testProcess");
        cy.layoutScenario();

        cy.contains(/^fragments$/)
            .should("exist")
            .scrollIntoView();
        cy.contains("fragment-test")
            .last()
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 800,
                    y: 600,
                },
                force: true,
            });
        cy.layoutScenario();

        cy.intercept("POST", "/api/nodes/*/validation").as("fragmentInputValidation");

        cy.get("[model-id^=e2e][model-id$=fragment-test-process]").should("be.visible").trigger("dblclick");
        cy.get("#nk-graph-fragment [model-id='input']").scrollIntoView().should("be.visible");

        cy.wait("@fragmentInputValidation");
        cy.get("[data-testid=window]").find("section").scrollTo("top");
        cy.get("[data-testid=window]").find('[data-testid="content-size"]').matchImage();

        cy.get("[data-testid=window]").find("section").scrollTo("bottom");
        cy.get("[data-testid=window]").find('[id="nk-graph-fragment"]').matchImage();

        cy.get('[title="name_string_any_with_suggestion"]').siblings().eq(0).find('[title="Switch to expression mode"]');
        cy.get('[title="name_string_fixed"]').siblings().eq(0).contains("#meta.processName");
        cy.get('[title="name_string_fixed"]').find('[title="Hint text test"]').should("be.visible");
        cy.get('[title="non_boolean_or_string"]').siblings().eq(0).contains("1");

        // any value with suggestions preset verification
        cy.get('[title="any_value_with_suggestions_preset"]').siblings().eq(0).as("anyValueWithSuggestionField");

        cy.get("@anyValueWithSuggestionField").find("input").should("have.value", "Email Marketing 12.2019");
        cy.get("@anyValueWithSuggestionField").clear().type("Campaign 2020");
        cy.get("[id$='option-0']").contains("Campaign 2020 News").click({ force: true });
        cy.get("@anyValueWithSuggestionField").find("input").should("have.value", "Campaign 2020 News");
        cy.get("@anyValueWithSuggestionField").find('[title="Switch to expression mode"]').click();

        // Expression should be clear after switch
        cy.get("@anyValueWithSuggestionField").should("have.value", "");
        cy.get("@anyValueWithSuggestionField").find("#ace-editor").type("#RGB()");

        cy.intercept("POST", "/api/nodes/*/validation").as("validation");
        cy.wait("@validation");

        cy.get("@anyValueWithSuggestionField").find("[data-testid='form-helper-text']").should("not.exist");

        cy.get("[data-testid=window]").find("input[value=testOutput]").type("{selectall}fragmentResult");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        cy.getNode("sendSms")
            .parent()
            .matchImage({
                screenshotConfig: {
                    padding: 16,
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
        cy.wait("@validation")
            .its("response.statusCode")
            .should("eq", 200)
            .then(() => {
                cy.get("@window").get("[title='Value']").siblings().eq(0).find("[data-testid='form-helper-text']").should("exist");
            });
        cy.wait("@suggestions").its("response.statusCode").should("eq", 200);
        cy.get(".ace_autocomplete").should("be.visible");

        cy.get("[data-testid=window]").matchImage({ maxDiffThreshold: 0.01 });

        // Save scenario
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        // Verify if Frontend received correct data after save
        cy.get("[model-id^=e2e][model-id$=fragment-test-process]").trigger("dblclick");
        cy.get('[title="any_value_with_suggestions_preset"]').siblings().eq(0).find("#ace-editor").contains("#RGB()");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        // Go back to the fragment
        cy.go(-1);

        // Provide new parameter to the fragment input
        cy.get("[model-id=input]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").should("be.visible").as("window");
        cy.get("@window").contains("+").click();
        cy.get("[data-testid='fieldsRow:8']").find("[placeholder='Field name']").type("test5");
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

        cy.get("@window")
            .get("[title='name_value_string_any_value']")
            .siblings()
            .eq(0)
            .find("[data-testid='form-helper-text']")
            .should("be.visible");

        cy.get("[data-testid=window]").find("section").scrollTo("top");
        cy.get("[data-testid=window]").find('[data-testid="content-size"]').matchImage();
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

        cy.get("[data-testid=window]").should("be.visible").find("input").eq(2).click().type(docsUrl);

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

        cy.contains("fragments").should("exist").scrollIntoView();
        cy.contains(`${seed2}-test`)
            .last()
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 800,
                    y: 600,
                },
                force: true,
            });
        cy.layoutScenario();

        cy.get(`[model-id^=e2e][model-id$=-${seed2}-test-process]`).should("be.visible").trigger("dblclick");

        cy.get("[title='Documentation']").should("have.attr", "href", docsUrl).parent().matchImage();

        cy.deleteAllTestProcesses({ filter: seed2 });
    });

    it("should display dead-ended fragment correct", () => {
        const fragmentName = "fragmentOutput";
        const deadEndFragmentName = "fragmentDeadEnd";
        cy.createTestFragment(fragmentName, "fragment").as("fragmentName");
        cy.visitNewProcess(seed, "testProcess").as("scenarioName");
        cy.createTestFragment(deadEndFragmentName, "deadEndFragment");
        cy.layoutScenario();

        cy.contains("fragments").should("exist").scrollIntoView();
        cy.getNode("enricher").as("enricher");
        cy.contains(`${fragmentName}-test`)
            .last()
            .should("be.visible")
            .drag("@enricher", {
                target: {
                    x: 250,
                    y: -20,
                },
                force: true,
            });
        cy.layoutScenario();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        cy.get<string>("@fragmentName").then((name) => cy.visitProcess(name));
        cy.contains("sinks").should("exist").scrollIntoView();
        cy.getNode("output").as("output");
        cy.contains("dead-end")
            .first()
            .should("be.visible")
            .drag("@output", {
                target: {
                    x: 0,
                    y: 0,
                },
                force: true,
            });
        cy.get("@output").click().type("{backspace}");
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();
        cy.contains(/^save$/i).should("be.disabled");

        cy.viewport(2000, 800);
        cy.get<string>("@scenarioName").then((name) => cy.visitProcess(name));
        cy.layoutScenario();
        cy.getNode("sendSms").as("sendSms");
        cy.contains("fragments").should("exist").scrollIntoView();
        cy.contains(`${deadEndFragmentName}-test`)
            .last()
            .should("be.visible")
            .drag("@sendSms", {
                target: {
                    x: 240,
                    y: -20,
                },
                force: true,
            });
        cy.layoutScenario();

        cy.get("@sendSms")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });
    });
});
