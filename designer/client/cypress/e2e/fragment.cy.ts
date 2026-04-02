function exportToFixture(fixture: string) {
    cy.intercept({ url: "**/api/processesExport/**", times: 1 }).as("fileDownload");
    cy.contains(/^export$/i).click();
    cy.wait("@fileDownload").then((interception) => {
        cy.writeFile(`cypress/fixtures/${fixture}.json`, interception.response.body);
    });
}

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
        cy.viewport("macbook-16");
    });

    describe("with advanced input parameters", () => {
        const toggleSettings = (fieldNumber: number) => {
            cy.get("@window").find(`[data-testid='fieldsRow:${fieldNumber}']`).find("[title='Options']").click();
        };
        const setFieldName = (fieldNumber: number, value: string) => {
            const fieldSelector = `[data-testid='fieldsRow:${fieldNumber}'] [placeholder='Field name']`;
            cy.window().then((win) => {
                cy.get("@window")
                    .find(fieldSelector)
                    .should("be.enabled")
                    .click({ force: true })
                    .then(($input) => {
                        const input = $input.get(0) as HTMLInputElement;
                        const nativeSetter = Object.getOwnPropertyDescriptor(win.HTMLInputElement.prototype, "value")?.set;
                        // Set full value at once to avoid flaky key-by-key typing on CI.
                        nativeSetter?.call(input, value);
                        input.dispatchEvent(new win.Event("input", { bubbles: true }));
                        input.dispatchEvent(new win.Event("change", { bubbles: true }));
                    });
            });
            cy.get("@window").find(fieldSelector).should("have.value", value);
        };

        const stableSettingsSnapshot = (settingsNumber: number) => {
            cy.wait(250);
            cy.get("[data-testid=window]").should("be.visible");
            cy.get(`[data-testid='settings:${settingsNumber}']`).scrollIntoView().should("be.visible").matchImage();
        };

        it("should allow adding input parameters", () => {
            cy.intercept("POST", "**/api/nodes/**/validation").as("nodeValidation");
            cy.visitNewFragment(seed, "fragment");

            cy.openNodeWindow("input").as("window");

            // Provide String Any Value inputMode
            const nameValueStringAnyValue = "name_value_string_any_value";
            cy.get("@window").contains("+").click();
            setFieldName(3, nameValueStringAnyValue);
            toggleSettings(3);

            cy.get("@window")
                .find("[data-testid='fieldsRow:3']")
                .find("[placeholder='Field name']")
                .should("have.value", nameValueStringAnyValue);
            // Validation response updates fields section; wait for it before screenshot to avoid detached subject.
            cy.wait("@nodeValidation");
            stableSettingsSnapshot(3);

            // Provide String Any with Suggestion Value inputMode
            cy.get("@window").contains("+").click();
            setFieldName(4, "name_string_any_with_suggestion");
            toggleSettings(4);
            cy.get("[data-testid='settings:4']").contains("Any value").select(1);

            // Display Add list item errors when blank value
            cy.get("[data-testid='settings:4']").contains("User defined list").click();
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
            cy.get("[data-testid='settings:4']").find("[data-testid='form-helper-text']").should("be.visible");
            cy.get("[data-testid='settings:4']").contains("This field is mandatory and can not be empty");

            // Display Add list item errors from a backend when Non reference value occurs
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("w");
            cy.get("[data-testid='settings:4']").click(); // click outside
            cy.get("[data-testid='settings:4']").find("[data-testid='form-helper-text']").should("be.visible");
            cy.get("[data-testid='settings:4']").contains("Non reference 'w' occurred. Maybe you missed '#' in front of it?");
            cy.get("[data-testid='settings:4']").find("[role='button']").contains(/^w$/).click();
            cy.get("[data-testid='settings:4']").find("[role='button']").should("not.exist");

            // Display Add list item value without error
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{backspace}");
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("#meta.processName");
            cy.get("[data-testid='settings:4']").contains("Typing...").should("not.exist");
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
            cy.get("[data-testid='settings:4']").find("[data-testid='form-helper-text']").should("not.exist");
            cy.get("[data-testid='settings:4']").find("[role='button']").contains("#meta.processName");

            // Display Add list item errors when a value is not unique
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("#meta.processName");
            cy.get("[data-testid='settings:4']").contains("Typing...").should("not.exist");
            cy.get("[data-testid='settings:4']").find("[id='ace-editor']").type("{enter}");
            cy.get("[data-testid='settings:4']")
                .contains(/Suggested values/i)
                .siblings()
                .eq(0)
                .find("[data-testid='form-helper-text']")
                .contains("not unique")
                .should("be.visible");

            stableSettingsSnapshot(4);

            // Provide String Fixed value inputMode
            cy.get("@window").contains("+").click();
            setFieldName(5, "name_string_fixed");
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
            stableSettingsSnapshot(5);

            // Provide non String or Boolean Any Value inputMode
            cy.get("@window").contains("+").click();
            setFieldName(6, "non_boolean_or_string");
            cy.get("@window").find("[data-testid='fieldsRow:6']").contains("String").click();
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

            stableSettingsSnapshot(6);

            // Provide String Fixed value inputMode
            cy.get("@window").contains("+").click();
            setFieldName(7, "any_value_with_suggestions_preset");
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

            // Provide String Fixed value inputMode
            cy.get("@window").contains("+").click();
            setFieldName(8, "generic_type");
            cy.get("@window").find("[data-testid='fieldsRow:8']").contains("String").click().type("List[String]{enter}");

            cy.get("@window")
                .contains(/^apply$/i)
                .click();

            cy.get("@window").should("not.exist");
            cy.verifySaveIndicator();
            cy.contains(/^save$/i).click();
            cy.contains(/^ok$/i).click();

            exportToFixture("fragmentWithInput");
        });

        it("should display used fragment graph in modal", () => {
            cy.createTestFragment(seed, "fragmentWithInput").as("fragmentName");
            cy.visitNewProcess(seed, "testProcess").as("processName");

            cy.layoutScenario();

            cy.contains(/^fragments$/)
                .should("exist")
                .scrollIntoView();

            cy.get<string>("@fragmentName").then((name) => {
                cy.contains(name)
                    .last()
                    .should("be.visible")
                    .drag("#nk-graph-main", {
                        target: {
                            x: 840,
                            y: 600,
                        },
                        force: true,
                    });
            });

            cy.layoutScenario();

            cy.intercept("POST", "/api/nodes/*/validation").as("fragmentInputValidation");
            cy.viewport(1600, 1200);

            cy.getNode("@fragmentName").trigger("dblclick");
            cy.get("#nk-graph-fragment [data-node-name='input']").scrollIntoView().should("be.visible");

            //FIXME flaky screenshot
            // cy.get("[data-testid=window]")
            //     .find("section")
            //     .scrollTo("top")
            //     .click(5, 5) //drop focus from input - avoid random scrolling/reszing of window contents
            //     .matchImage();

            cy.get("[data-testid=window]").find("section").scrollTo("bottom").find('[id="nk-graph-fragment"]').should("be.visible");
            //FIXME flaky screenshot
            // cy.getNode("output")
            //     .parent()
            //     .matchImage({
            //         maxDiffThreshold: 0.02,
            //         screenshotConfig: {
            //             padding: 8,
            //         },
            //     });
        });

        it("should validate and save changes", function () {
            cy.createTestFragment(seed, "fragmentWithInput").as("fragmentName");
            cy.visitNewProcess(seed, "testProcess").as("processName");

            cy.layoutScenario();

            cy.contains(/^fragments$/)
                .should("exist")
                .scrollIntoView();

            cy.get<string>("@fragmentName").then((name) => {
                cy.contains(name)
                    .last()
                    .should("be.visible")
                    .drag("#nk-graph-main", {
                        target: {
                            x: 840,
                            y: 600,
                        },
                        force: true,
                    });
            });

            cy.openNodeWindow("@fragmentName");

            cy.get('[title="name_string_any_with_suggestion"]').siblings().eq(0).find("[role=tab]").contains("fixed values");
            cy.get('[title="name_string_fixed"]').siblings().eq(0).contains("#meta.processName");
            cy.get('[title="name_string_fixed"]').find('[data-testid="InfoIcon"]').should("be.visible");
            cy.get('[title="non_boolean_or_string"]').siblings().eq(0).contains("1");

            // any value with suggestions preset verification
            cy.get(`[title="any_value_with_suggestions_preset"]`).siblings().eq(0).as("anyValueWithSuggestionField");
            cy.get("@anyValueWithSuggestionField").find("input").should("have.value", "Email Marketing 12.2019");
            cy.get("@anyValueWithSuggestionField").clear().type("Campaign 2020");
            cy.get("[id$='option-0']").contains("Campaign 2020 News").click({ force: true });
            cy.get("@anyValueWithSuggestionField").find("input").should("have.value", "Campaign 2020 News");
            cy.get("@anyValueWithSuggestionField").find("[role=tab]").contains("expression").click();

            // Expression should be clear after switch
            cy.get("@anyValueWithSuggestionField").should("have.value", "");
            cy.get("@anyValueWithSuggestionField").find("#ace-editor").type("'H000000'");

            cy.intercept("POST", "/api/nodes/*/validation").as("validation");
            cy.wait("@validation");

            cy.get("@anyValueWithSuggestionField").find("[data-testid='form-helper-text']").should("not.exist");

            cy.get("[data-testid=window]").get('[title="testOutput"]').siblings().eq(0).find("input").type("{selectall}fragmentResult");
            cy.applyNodeChanges();

            cy.layoutScenario();
            cy.getNode("sendSms")
                .parent()
                .matchImage({
                    screenshotConfig: {
                        padding: 16,
                    },
                });

            cy.openNodeWindow("sendSms");
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
            cy.get("[data-testid=window]")
                .find('[title="Value"]')
                .siblings()
                .eq(0)
                .find("#ace-editor")
                .should("be.visible")
                .type("{selectall}#fragmentResult.");
            cy.wait("@suggestions").its("response.statusCode").should("eq", 200);
            cy.get(".ace_autocomplete").should("be.visible");

            cy.get("[data-testid=window]").as("window").matchImage({ maxDiffThreshold: 0.01 });

            // Save scenario
            cy.applyNodeChanges();
            cy.get("@window").should("not.exist");
            cy.verifySaveIndicator();
            cy.contains(/^save$/i).click();
            cy.contains(/^ok$/i).click();
            cy.get("@window").should("not.exist");

            // Verify if Frontend received correct data after save
            cy.openNodeWindow("@fragmentName");
            cy.get('[title="any_value_with_suggestions_preset"]').siblings().eq(0).find("#ace-editor").contains("'H000000'");
            cy.get("@window").get("[title='test5']").should("not.exist");

            cy.applyNodeChanges();
            cy.get("@window").should("not.exist");

            cy.visitProcess("@fragmentName");

            // Provide new parameter to the fragment input
            cy.openNodeWindow("input");
            cy.get("@window").should("be.visible").contains("+").click();
            setFieldName(9, "test5");
            cy.get("@window")
                .contains(/^apply$/i)
                .click();

            cy.get("@window").should("not.exist");
            cy.verifySaveIndicator();
            cy.contains(/^save$/i).click();
            cy.contains(/^ok$/i).click();

            // Go back to the Scenario
            cy.go(-1);

            // Verify existing fragment after properties change
            cy.openNodeWindow("@fragmentName");
            cy.get("@window").get("[title='name_value_string_any_value']").get("[role=tab]").contains("expression").click();

            cy.get("@window").get("[title='test5']").should("exist");

            cy.get("@window")

                .get("[title='name_value_string_any_value']")
                .siblings()
                .eq(0)
                .find("[id='ace-editor']")
                .type("test");

            cy.viewport(1600, 1200);

            cy.get("@window")
                .get("[title='name_value_string_any_value']")
                .siblings()
                .eq(0)
                .find("[data-testid='form-helper-text']")
                .should("be.visible");

            cy.get("@window").find("section").scrollTo("top").find('[data-testid="content-size"]').matchImage();
        });
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

        cy.applyNodeChanges();
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

        cy.get(`[data-node-name^=e2e][data-node-name$=-${seed2}-test-process]`).should("be.visible").trigger("dblclick");

        cy.get("[title='Documentation']").should("have.attr", "href", docsUrl).parent().matchImage({
            maxDiffThreshold: 0.04,
        });

        cy.deleteAllTestProcesses({ filter: seed2 });
    });

    it("should display dead-ended fragment correct", () => {
        cy.createTestFragment("fragmentOutput", "fragment").as("fragmentName");
        cy.createTestFragment("fragmentDeadEnd", "deadEndFragment").as("deadEndFragmentName");
        cy.visitNewProcess(seed, "testProcess").as("scenarioName");
        cy.layoutScenario();

        cy.contains("fragments").should("exist").scrollIntoView();
        cy.getNode("enricher").as("enricher");

        cy.get<string>("@fragmentName").then((name) => {
            cy.contains(name).last().should("be.visible").as("f1");
        });
        cy.get("@enricher").then((node) => {
            cy.get("@f1").drag("@enricher", {
                target: {
                    x: (node.width() * 4) / 5,
                    y: -node.height() / 2,
                },
                force: true,
            });
        });
        cy.layoutScenario();

        cy.verifySaveIndicator();
        cy.contains(/^save$/i).click();
        cy.contains(/^ok$/i).click();

        cy.visitProcess("@fragmentName");
        cy.contains("sinks").should("exist").scrollIntoView();
        cy.getNode("output").should("be.visible").click().as("output");

        cy.contains(/^dead end$/i)
            .first()
            .should("be.visible")
            .as("source");
        cy.get("@output").then((node) => {
            /*
             *  FIXME: for some unknown for now reason first monitor.getClientOffset() reports wrong values
             *  check src/components/graph/ProcessGraph.tsx:89
             *  */
            cy.get("@source").move({ deltaX: 1, deltaY: 1 });
            cy.get("@source").drag("@output", {
                force: true,
                target: {
                    x: node.width() / 2,
                    y: (node.height() * 3) / 4,
                },
            });
        });
        cy.get("@output").click({ force: true }).type("{backspace}");
        cy.layoutScenario();

        cy.verifySaveIndicator();
        cy.contains(/^save$/i).click();
        cy.contains(/^ok$/i).click();
        cy.contains(/^save$/i).should("be.disabled");

        cy.visitProcess("@scenarioName");
        cy.viewport(2000, 800);
        cy.layoutScenario();

        cy.getNode("sendSms").should("be.visible").click().as("sendSms");
        cy.contains("fragments").should("exist").scrollIntoView();
        cy.get<string>("@deadEndFragmentName").then((name) => {
            cy.contains(name).last().should("be.visible").as("f2");
        });

        cy.intercept({ url: "/api/processValidation/**", times: 1 }).as("processValidation");
        cy.get("@sendSms").then((node) => {
            cy.get("@f2").drag("@sendSms", {
                target: {
                    x: (node.width() * 4) / 5,
                    y: -node.height() / 2,
                },
                force: true,
            });
        });

        cy.viewport("macbook-16");
        cy.layoutScenario();
        cy.wait("@processValidation");

        cy.get("@sendSms")
            .parent()
            .matchImage({
                screenshotConfig: { padding: 16 },
            });
    });
});
