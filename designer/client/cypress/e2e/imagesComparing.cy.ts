describe("Images comparing", () => {
    const seed = "imagesComparing";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.viewport(1920, 1080);
        // cy.visit("/scenarios");
    });

    afterEach(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    it("should display - basic components - variable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsVariable#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });

        cy.get('[model-id="My first variable declaration"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();

        cy.visitNewProcess(seed, "docsBasicComponentsVariable#1");
        cy.get('[model-id="only financial ops"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });

    it("should display - basic components - mapmariable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsMapVariable#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="node label goes here"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
        cy.get(".window-close").click();

        cy.get('[model-id="variable"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });

    it("should display - basic components - filter", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsFilter#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });

        cy.visitNewProcess(seed, "docsBasicComponentsFilter#1");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });

        cy.get('[model-id="conditional filter"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });

    it.skip("should display - basic components - choice", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsChoice#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });

        cy.get('[model-id="choice"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    it("should display - basic components - split", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsSplit#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });
    });

    it("should display - basic components - foreach", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsForEach#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="for-each"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });

    it.skip("should display - basic components - union", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsUnion#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });

        cy.get('[model-id="union"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });

    it.skip("should display - aggregates - Single Side Join", () => {
        cy.visitNewProcess(seed, "docsAggregatesSingleSideJoin#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get(".joint-layers").matchImage({ screenshotConfig: { padding: 16 } });

        cy.get('[model-id="single-side-join"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });

    it.skip("should display - aggregates - Full Outer Join", () => {
        cy.visitNewProcess(seed, "docsAggregatesFullOuterJoin#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="full-outer-join"]').dblclick();
        cy.get('[title="Name"]').click();
        cy.get('[data-testid="window-frame"]').matchImage();
    });
});
