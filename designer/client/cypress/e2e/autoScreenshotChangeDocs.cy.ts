import { join } from "path";

describe("Auto Screenshot Change Docs -", () => {
    const seed = "autoScreenshotChangeDocs";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.viewport(1400, 800);
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    it("basic components - variable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsVariable#0"); // load scenario
        cy.layoutScenario(); // layout alignment
        takeGraphScreenshot(); // take screenshot of whole graph

        cy.openNodeWindow("My first variable declaration");
        takeWindowScreenshot(); // take screenshot of node window

        cy.visitNewProcess(seed, "docsBasicComponentsVariable#1"); // load new scenario
        cy.openNodeWindow("only financial ops");
        takeWindowScreenshot(); // take screenshot of node window
    });

    it("basic components - recordVariable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsRecordVariable#0");
        cy.layoutScenario();
        cy.openNodeWindow("node label goes here");
        takeWindowScreenshot();
        cy.get("[data-testid=window]")
            .contains(/^cancel$/i)
            .click();

        cy.openNodeWindow("variable");
        takeWindowScreenshot();
    });

    it("basic components - filter", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsFilter#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.visitNewProcess(seed, "docsBasicComponentsFilter#1");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.openNodeWindow("conditional filter");
        takeWindowScreenshot();
    });

    it("basic components - choice", () => {
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsChoice#0");
        cy.get("[title='toggle left panel']").click();
        cy.layoutScenario();
        cy.get("[title='toggle right panel']").click();
        takeGraphScreenshot();

        cy.openNodeWindow("choice");
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
        cy.openNodeWindow("for-each");
        takeWindowScreenshot();
    });

    it("basic components - union", () => {
        cy.viewport(1920, 1080);
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsUnion#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.openNodeWindow("union");
        takeWindowScreenshot();
    });

    it("aggregates - Single Side Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesSingleSideJoin#0");
        cy.layoutScenario();
        takeGraphScreenshot();

        cy.openNodeWindow("single-side-join");
        takeWindowScreenshot();
    });

    it("aggregates - Full Outer Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesFullOuterJoin#0");
        cy.layoutScenario();
        cy.openNodeWindow("full-outer-join");
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
        cy.openNodeWindow("input");
        takeWindowScreenshot();

        cy.get('[title="Options"]').eq(0).click(); // open parameter1 options
        takeWindowScreenshot();

        cy.get('[title="Options"]').eq(0).click(); // close parameter1 options
        cy.get('[title="Options"]').eq(1).click(); // open parameter2 options
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

const projectRoot = join(Cypress.config("fileServerFolder"), "../..");
const snapshotOptions = {
    maxDiffThreshold: 0.009,
    imagesPath: join(projectRoot, "docs/autoScreenshotChangeDocs"),
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
