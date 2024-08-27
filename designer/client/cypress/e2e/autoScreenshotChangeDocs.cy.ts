import * as path from "path";

describe("Auto Screenshot Change Docs -", () => {
    const seed = "autoScreenshotChangeDocs";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.viewport(1400, 800);
    });

    afterEach(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("basic components - variable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsVariable#0"); // load scenario
        cy.layoutScenario(); // layout alignment
        takeGraphScreenshot(); // take screenshot of whole graph

        cy.get('[model-id="My first variable declaration"]').dblclick(); // click on node
        cy.get('[title="Name"]').click(); // click of remove cursor flickering effect
        takeWindowScreenshot(); // take screenshot of node window

        cy.visitNewProcess(seed, "docsBasicComponentsVariable#1"); // load new scenario
        cy.get('[model-id="only financial ops"]').dblclick(); // click on node
        cy.get('[title="Name"]').click(); // click of remove cursor flickering effect
        takeWindowScreenshot(); // take screenshot of node window
    });

    it("basic components - recordVariable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsRecordVariable#0");
        cy.layoutScenario();
        cy.get('[model-id="node label goes here"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();

        cy.get('[model-id="variable"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - filter", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsFilter#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.visitNewProcess(seed, "docsBasicComponentsFilter#1");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="conditional filter"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - choice", () => {
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsChoice#0");
        cy.get("[title='toggle left panel']").click();
        cy.layoutScenario();
        cy.get("[title='toggle right panel']").click();
        takeGraphScreenshot();

        cy.get('[model-id="choice"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - split", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsSplit#0");
        cy.layoutScenario();
        takeGraphScreenshot();
    });

    it("basic components - foreach", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsForEach#0");
        cy.layoutScenario();
        cy.get('[model-id="for-each"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("basic components - union", () => {
        cy.viewport(1920, 1080);
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsUnion#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="union"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("aggregates - Single Side Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesSingleSideJoin#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.get('[model-id="single-side-join"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("aggregates - Full Outer Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesFullOuterJoin#0");
        cy.layoutScenario();
        cy.get('[model-id="full-outer-join"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("fragments - Properties", () => {
        cy.viewport(1920, 1080);
        cy.visitNewFragment(seed, "fragment").as("fragmentName");

        cy.contains(/^properties/i).click();

        takeWindowScreenshot();
    });

    it("fragments - Inputs", () => {
        cy.viewport(1920, 1080);
        cy.visitNewProcess(seed, "docsFragmentsInputs#0");
        cy.layoutScenario();
        cy.get('[model-id="input"]').dblclick();
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();

        cy.get('[title="Options"]').eq(0).click(); // open parameter1 options
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();

        cy.get('[title="Options"]').eq(0).click(); // close parameter1 options
        cy.get('[title="Options"]').eq(1).click(); // open parameter2 options
        cy.get('[title="Name"]').click();
        takeWindowScreenshot();
    });

    it("fragments - Outputs", () => {
        cy.viewport(1920, 1080);
        cy.visitNewProcess(seed, "docsFragmentsOutputs#0");
        cy.layoutScenario();
        takeGraphScreenshot();
    });
});

// screenshots CONSTANT options DO NOT CHANGE

const projectRoot = path.join(Cypress.config("fileServerFolder"), "../..");
const snapshotOptions = {
    maxDiffThreshold: 0.009,
    imagesPath: path.join(projectRoot, "docs/autoScreenshotChangeDocs"),
};

// screenshots taking functions

function takeGraphScreenshot() {
    cy.get('[joint-selector="layers"]').matchImage({
        ...snapshotOptions,
        screenshotConfig: { padding: 20 },
    });
}

function takeWindowScreenshot() {
    cy.get('[data-testid="window-frame"]').matchImage(snapshotOptions);
}
