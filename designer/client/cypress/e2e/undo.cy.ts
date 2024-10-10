describe("Undo/Redo", () => {
    const seed = "undo";
    const screenshotOptions: Cypress.MatchImageOptions = {
        maxDiffThreshold: 0.005,
        screenshotConfig: {
            blackout: ["> div > :not(#nk-graph-main) > div"],
        },
    };

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "testProcess");
        cy.contains(/^custom$/)
            .should("exist")
            .scrollIntoView();
        cy.layoutScenario();
        cy.get("[data-testid=graphPage]", { timeout: 20000 }).as("graph");
        cy.contains(/^undo$/i)
            .as("undo")
            .should("be.disabled");
        cy.contains(/^redo$/i)
            .as("redo")
            .should("be.disabled");
        cy.contains(/^copy$/i).as("copy");
        cy.contains(/^paste$/i).as("paste");
    });

    it("should work for add and move to edge", () => {
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("[data-testid='component:customFilter']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 480,
                    y: 450,
                },
                force: true,
            });
        cy.get("@graph").matchImage(screenshotOptions);
        cy.dragNode("customFilter", { x: 560, y: 500 });
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@undo").should("be.enabled").click();
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@undo").should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@redo").should("be.enabled").click();
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@redo").should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
    });

    it("should work for drop on edge", () => {
        cy.get("@graph").matchImage(screenshotOptions);
        cy.getNode("boundedSource-enricher").find("[event=remove]").eq(0).click();
        cy.getNode("enricher-dynamicService").find("[event=remove]").eq(0).click();
        cy.get("[data-testid='component:customFilter']")
            .should("be.visible")
            .drag("#nk-graph-main", {
                target: {
                    x: 580,
                    y: 450,
                },
                force: true,
            });
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@undo").should("be.enabled").click().should("be.enabled").click().should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@redo").should("be.enabled").click().should("be.enabled").click().should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
    });

    it("should work for move", () => {
        cy.get("@graph").matchImage(screenshotOptions);
        cy.dragNode("enricher", { x: 560, y: 500 });
        cy.dragNode("enricher", { x: 560, y: 400 });
        cy.dragNode("enricher", { x: 560, y: 200 });
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@undo").should("be.enabled").click().click().click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@redo").should("be.enabled").click().click();
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@redo").click().should("be.disabled");
    });

    it("should work for copy/paste", () => {
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("body").type("{ctrl}a");
        cy.get("@copy").click();
        cy.get("@undo").should("be.enabled").click().should("be.disabled");
        cy.get("@paste").click();
        cy.get("@undo").should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
        cy.get("@redo").should("be.enabled").click().should("be.disabled");
    });

    it("should work with counts", () => {
        cy.intercept("GET", "/api/processCounts/*", {
            boundedSource: { all: 10, errors: 0, fragmentCounts: {} },
            enricher: { all: 120, errors: 10, fragmentCounts: {} },
            dynamicService: { all: 40, errors: 0, fragmentCounts: {} },
            sendSms: { all: 60, errors: 0, fragmentCounts: {} },
        });

        cy.dragNode("enricher", { x: 560, y: 500 });
        cy.get("@undo").should("be.enabled").click().should("be.disabled");

        cy.contains(/^counts$/i).click();
        cy.get("[data-testid=window]").contains(/^ok$/i).click();

        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });

        cy.get("@redo").should("be.enabled").click().should("be.disabled");

        cy.getNode("enricher")
            .parent()
            .matchImage({ screenshotConfig: { padding: 16 } });

        cy.wait(500);
    });

    it("should work with validation", () => {
        cy.dragNode("enricher", { x: 560, y: 500 });
        cy.get("@undo").should("be.enabled");

        cy.getNode("enricher").click();
        cy.contains(/^delete$/i).click();

        cy.get("@undo").should("be.enabled").click().should("be.enabled").click().should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);

        cy.get("@redo").should("be.enabled").click().should("be.enabled").click().should("be.enabled").click().should("be.disabled");
        cy.get("@graph").matchImage(screenshotOptions);
    });
});
