import axios from "axios";
describe("Images Comparing", () => {
    const seed = "imagesComparing";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.viewport(1920, 1080);
        postSchemaToRegistry();
    });

    afterEach(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    it("should display - basic components - variable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsVariable#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);

        cy.get('[model-id="My first variable declaration"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
        cy.visitNewProcess(seed, "docsBasicComponentsVariable#1");
        cy.get('[model-id="only financial ops"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it("should display - basic components - mapmariable", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsMapVariable#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="node label goes here"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
        cy.get(".window-close").click();

        cy.get('[model-id="variable"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it("should display - basic components - filter", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsFilter#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);

        cy.visitNewProcess(seed, "docsBasicComponentsFilter#1");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);

        cy.get('[model-id="conditional filter"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it.skip("should display - basic components - choice", () => {
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsChoice#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);

        cy.get('[model-id="choice"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it("should display - basic components - split", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsSplit#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);
    });

    it("should display - basic components - foreach", () => {
        cy.visitNewProcess(seed, "docsBasicComponentsForEach#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="for-each"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it.skip("should display - basic components - union", () => {
        //skip
        cy.visitNewProcess(seed, "docsBasicComponentsUnion#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);

        cy.get('[model-id="union"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it.skip("should display - aggregates - Single Side Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesSingleSideJoin#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);

        cy.get('[model-id="single-side-join"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it.skip("should display - aggregates - Full Outer Join", () => {
        //skip
        cy.visitNewProcess(seed, "docsAggregatesFullOuterJoin#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="full-outer-join"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it("should display - fragments - Inputs", () => {
        cy.visitNewProcess(seed, "docsFragmentsInputs#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="input"]').dblclick();
        cy.get('[title="Name"]').click();
        imageTake();
    });

    it("should display - fragments - Outputs", () => {
        cy.visitNewProcess(seed, "docsFragmentsOutputs#0");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        imageTake(true);
    });

    it.only("should display kawka value", () => {
        cy.visitNewProcess(seed, "test-7");
        cy.get('[title="layout"] > .ToolbarButton--icon--w9M5n').click();

        cy.get('[model-id="kafka"]').dblclick();
        imageTake();
    });
});

function imageTake(padding = false) {
    const basePath = "/home/fgl/Git/nussknacker/docs/scenarios_authoring/img/imagesComparing/";
    cy.get(padding ? ".joint-layers" : '[data-testid="window-frame"]').matchImage({
        screenshotConfig: { padding: padding ? 16 : 0 },
        imagesPath: basePath,
    });
}

function postSchemaToRegistry(): void {
    const url = "http://localhost:8081/subjects/Kafka-value/versions";
    const headers = {
        "Content-Type": "application/vnd.schemaregistry.v1+json",
    };
    const data = {
        "nu-value":
            '{"type": "object","additionalProperties": false,"$schema": "http://json-schema.org/draft-07/schema","required": ["operation","color"],"properties": {"color": {"type": "string"},"operation": {"type": "string"}}}',
    };

    axios
        .post(url, data, { headers })
        .then((response) => {
            console.log("Response:", response.data);
        })
        .catch((error) => {
            console.error("Error:", error);
        });
}
