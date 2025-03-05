describe("Search Panel View", () => {
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

    it("should collapse advanced search filters", () => {
        cy.get("[data-testid=search-panel]").should("be.visible");
        cy.get("[data-testid=search-panel]").contains(/^search$/i);
        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        //cy.realType("se");
        cy.get("[data-testid=search-panel]").contains("Advanced Search");
        cy.get("[data-testid=search-panel]").contains("Name");
        cy.get("[data-testid=search-panel]").contains("Description");
        cy.get("[data-testid=search-panel]").contains("Label");
        cy.get("[data-testid=search-panel]").contains("Value");
        cy.get("[data-testid=search-panel]").contains("Output");
        cy.get("[data-testid=search-panel]").contains("Type");
        cy.get("[data-testid=search-panel]").contains("Edge");
    });

    it("should filter nodes when typing search query with selectors manually and editing it later in form", () => {
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click();

        cy.realType("type:(sink) se");

        cy.get("[data-testid=search-panel]").contains("dynamicService").should("not.exist");
        cy.get("[data-testid=search-panel]").contains("sendSms");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("input[name='type']").should("have.value", "sink");

        cy.get("[data-testid=search-panel]").find("input[name='type']").click();
        cy.realType(",dynamicService");

        cy.get("[data-testid=search-panel]").find("button[type='submit']").click();

        cy.get("[data-testid=search-panel]").contains("dynamicService");
        cy.get("[data-testid=search-panel]").contains("sendSms");
    });

    it("should filter nodes when performing simple search and adding selectors using form", () => {
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click();
        cy.realType("se");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("input[name='type']").click();

        cy.realType("sink,dynamicSe");

        cy.get("[data-testid=search-panel]").find("button[type='submit']").click();

        cy.get("[data-testid=search-panel]")
            .find("input[data-selector='NODES_IN_SCENARIO']")
            .should("have.value", "type:(sink,dynamicSe) se");

        cy.get("[data-testid=search-panel]").contains("dynamicService");
        cy.get("[data-testid=search-panel]").contains("sendSms");
    });

    it("should filter nodes when setting up multiple selectors using form", () => {
        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();

        cy.get("[data-testid=search-panel]").find("input[name='name']").click();
        cy.realType("bounded,dynamic,send,enricher");

        cy.get("[data-testid=search-panel]").find("input[name='type']").click();
        cy.realType("sink,enricher");

        cy.get("[data-testid=search-panel]").find("button[type='submit']").click();

        cy.get("[data-testid=search-panel]")
            .find("input[data-selector='NODES_IN_SCENARIO']")
            .should("have.value", "name:(bounded,dynamic,send,enricher) type:(sink,enricher)");

        cy.get("[data-testid=search-panel]").contains("enricher");
        cy.get("[data-testid=search-panel]").contains("sendSms");
    });

    it("should synchronize the form input state with manually provided query with selectors", () => {
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click();
        cy.realType("type:(sink)");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("input[name='type']").should("have.value", "sink");

        cy.get("[data-testid=search-panel]")
            .find("input[data-selector='NODES_IN_SCENARIO']")
            .click()
            .type("{moveToEnd}")
            .type("{leftArrow}")
            .type(",source");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("input[name='type']").should("have.value", "sink,source");
    });

    it("should clear search filters", () => {
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click();
        cy.realType("type:(sink) se");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("button[type='button']").click();

        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").should("have.value", "se");
        cy.get("[data-testid=search-panel]").find("input[name='type']").should("have.value", "");

        cy.get("[data-testid=search-panel]").contains("dynamicService");
        cy.get("[data-testid=search-panel]").contains("sendSms");
    });

    it("should clear unapplied search filters", () => {
        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("button[type='button']").click();

        cy.get("[data-testid=search-panel]").find("input[name='name']").click();
        cy.realType("sink");

        cy.get("[data-testid=search-panel]").find("button[type='button']").click();

        cy.get("[data-testid=search-panel]").find("input[name='name']").should("have.value", "");
    });

    it("should clear filters when clear all button clicked", () => {
        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").click();
        cy.realType("se");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("input[name='type']").click();

        cy.realType("sink,processor");

        cy.get("[data-testid=search-panel]").find("button[type='submit']").click();
        cy.get("[data-testid=search-panel]").find("svg[id='clear-icon']").click();

        cy.get("[data-testid=search-panel]").find("input[data-selector='NODES_IN_SCENARIO']").should("have.value", "");

        cy.get("[data-testid=search-panel]").find("svg[id='advanced-search-icon']").click();
        cy.get("[data-testid=search-panel]").find("input[name='type']").should("have.value", "");
    });
});
