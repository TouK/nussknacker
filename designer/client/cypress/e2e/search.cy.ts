describe("Search Panel View", { testIsolation: false }, () => {
    const seed = "process";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed });
        cy.visitNewProcess(seed, "testProcess");
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("input[data-selector='NODES_IN_SCENARIO']").type("{selectAll}{backspace}");
        });
    });

    it("should collapse advanced search filters", () => {
        cy.get("[data-testid=search-panel]")
            .should("be.visible")
            .within(() => {
                cy.contains(/^search$/i);
                cy.get("svg[id='advanced-search-icon']").click();
                cy.contains("Advanced Search");
                cy.contains("Name");
                cy.contains("Description");
                cy.contains("Label");
                cy.contains("Value");
                cy.contains("Output");
                cy.contains("Type");
                cy.contains("Edge");
            });
    });

    it("should filter nodes when typing search query with selectors manually and editing it later in form", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("input[data-selector='NODES_IN_SCENARIO']").click().type("type:(sink) se");

            cy.contains("dynamicService").should("not.exist");
            cy.contains("sendSms");

            cy.get("svg[id='advanced-search-icon']").click();
            cy.get("input[name='type']").should("have.value", "sink");

            cy.get("input[name='type']").click().type(",dynamicService");

            cy.contains(/^submit$/i).click();

            cy.contains("dynamicService");
            cy.contains("sendSms");
        });
    });

    it("should filter nodes when performing simple search and adding selectors using form", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("input[data-selector='NODES_IN_SCENARIO']").click().type("se");

            cy.get("svg[id='advanced-search-icon']").click();
            cy.get("input[name='type']").click().type("sink,dynamicSe");

            cy.contains(/^submit$/i).click();

            cy.get("input[data-selector='NODES_IN_SCENARIO']").should("have.value", "type:(sink,dynamicSe) se");

            cy.contains("dynamicService");
            cy.contains("sendSms");
        });
    });

    it("should filter nodes when setting up multiple selectors using form", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("svg[id='advanced-search-icon']").click();

            cy.get("input[name='name']").click().type("bounded,dynamic,send,enricher");

            cy.get("input[name='type']").click().type("sink,enricher");

            cy.contains(/^submit$/i).click();

            // the order of selectors in the composed query is not deterministic
            cy.get("input[data-selector='NODES_IN_SCENARIO']").should(($input) => {
                const value = String($input.val());
                expect(value).to.contain("name:(bounded,dynamic,send,enricher)");
                expect(value).to.contain("type:(sink,enricher)");
            });

            cy.contains("enricher");
            cy.contains("sendSms");
        });
    });

    it("should synchronize the form input state with manually provided query with selectors", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("input[data-selector='NODES_IN_SCENARIO']").click().type("type:(sink)");

            cy.get("svg[id='advanced-search-icon']").click();
            cy.get("input[name='type']").should("have.value", "sink");

            cy.get("input[data-selector='NODES_IN_SCENARIO']").click().type("{moveToEnd}{leftArrow},source");

            cy.contains("boundedSource").should("be.visible");
            cy.get("svg[id='advanced-search-icon']").click();
            cy.get("input[name='type']").should("have.value", "sink,source");
        });
    });

    it("should clear search filters", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("input[data-selector='NODES_IN_SCENARIO']").click().type("type:(sink) se");

            cy.get("svg[id='advanced-search-icon']").click();
            cy.contains(/^clear$/i).click();

            cy.get("input[data-selector='NODES_IN_SCENARIO']").should("have.value", "se");
            cy.get("input[name='type']").should("have.value", "");

            cy.contains("dynamicService");
            cy.contains("sendSms");
        });
    });

    it("should clear unapplied search filters", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("svg[id='advanced-search-icon']").click();
            cy.contains(/^clear$/i).click();

            cy.get("input[name='name']").click().type("sink");

            cy.contains(/^clear$/i).click();

            cy.get("input[name='name']").should("have.value", "");
        });
    });

    it("should clear filters when clear all button clicked", () => {
        cy.get("[data-testid=search-panel]").within(() => {
            cy.get("input[data-selector='NODES_IN_SCENARIO']").click().type("se");

            cy.get("svg[id='advanced-search-icon']").click();
            cy.get("input[name='type']").click().type("sink,processor");

            cy.contains(/^submit$/i).click();
            cy.get("svg[id='clear-icon']").click();

            cy.get("input[data-selector='NODES_IN_SCENARIO']").should("have.value", "");

            cy.get("svg[id='advanced-search-icon']").click();
            cy.get("input[name='type']").should("have.value", "");
        });
    });
});
