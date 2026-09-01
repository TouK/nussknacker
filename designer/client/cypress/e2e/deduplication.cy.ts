import { recurse } from "cypress-recurse";

type PersistedNode = {
    id: string;
    name: string;
};

type PersistedEdge = {
    from: string;
    to: string;
    edgeType?: { type: string; name?: string };
};

describe("Deduplication outputs", () => {
    const seed = "deduplication";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed);
    });

    it("shows the passed and rejected labels once both outputs are connected", () => {
        cy.intercept("POST", "/api/*Validation/*", (req) => {
            if (req.body.scenarioGraph.edges.length === 3) {
                req.alias = "validation";
            }
        });

        cy.contains(/collapse sidebar/i).click();
        cy.viewport(1500, 800);

        const linkCount = () => cy.get("body").then(($body) => $body.find("#nk-graph-main .joint-link").length);

        // JointJS snaps the dragged arrowhead only within `snapLinks.radius` (30px) of a magnet, so `dndTo`
        // aims at the centre of the target port itself. A node that has just landed can still be moving as
        // the paper pans, hence the retry until a link actually appears.
        const connectFreeOutputTo = (fromNodeName: string, toNodeName: string) => {
            const dragOutputToInput = () =>
                cy.getNode(fromNodeName).find('circle[port="Out"]').dndTo(`[data-node-name="${toNodeName}"] circle[port="In"]`);

            linkCount().then((before) =>
                recurse(
                    () => {
                        dragOutputToInput();
                        return linkCount();
                    },
                    (after) => after > before,
                    { limit: 4, delay: 500 },
                ),
            );
        };

        // Only `value` and `ttl` lack a defaultValue in the component definition. Fields are matched on the
        // parameter name the label carries in its `title` (FieldLabel.tsx). Each edit re-validates the node
        // and disables the form meanwhile; applying can re-open the window from the `?nodeId=` query param
        // (useModalsIfNeeded), so it is closed until it stays closed.
        const fillDeduplicationParameters = () => {
            cy.openNodeWindow("Deduplication");
            cy.get("@nodeWindow")
                .find('[title="ttl"]')
                .siblings()
                .contains(".time-range-component", "minutes")
                .find("input")
                .should("be.enabled")
                .type("1");
            cy.wait("@nodeValidation");
            cy.get("@nodeWindow").find('[title="value"]').siblings().find("[id='ace-editor']").should("be.visible").type("#input");
            cy.wait("@nodeValidation");

            cy.get("[data-testid=window]")
                .contains(/^apply$/i)
                .should("be.enabled")
                .click({ force: true });
            recurse(
                () => cy.get("body").then(($body) => $body.find("[data-testid=window]").length),
                (openWindows) => openWindows === 0,
                {
                    limit: 10,
                    delay: 300,
                    post: () => cy.get("body").then(($body) => $body.find('[data-testid=window] button[name="close"]').get(0)?.click()),
                },
            );
        };

        // Where the free space is depends on the paper's pan and zoom, and the paper re-centres its content
        // every time a node is added - so a fixed drop point drifts onto what is already there. Everything is
        // measured from the live cells instead: nodes and links alike, since a drop covering either is read as
        // a replacement of that node or an injection into that link (ProcessGraph `drop` -> addNodeReplace /
        // addNodeInject). The preview box is a node wide and reaches 0.8 of that width left of the cursor
        // (usePreviewOffset), hence the 0.7 - it leaves half a node of clearance beside the scenario.
        const dropTargetBesideScenario = (paper: Element) => {
            const paperBox = paper.getBoundingClientRect();
            const nodes = Array.from(paper.querySelectorAll("[data-node-name]"), (node) => node.getBoundingClientRect());
            const cells = Array.from(paper.querySelectorAll(".joint-cell"), (cell) => cell.getBoundingClientRect());

            if (!nodes.length) {
                return { x: paperBox.width / 2, y: paperBox.height / 2 };
            }

            const left = Math.min(...cells.map((cell) => cell.left));
            const middle = (Math.min(...cells.map((cell) => cell.top)) + Math.max(...cells.map((cell) => cell.bottom))) / 2;
            return { x: left - paperBox.left - 0.7 * nodes[0].width, y: middle - paperBox.top };
        };

        // A drop that lands on a cell leaves no node of its own behind, so it is checked rather than left to
        // surface as a missing port several steps later.
        const dropComponent = (group: RegExp, component: string, addedNodeName: string) => {
            cy.contains(group).should("exist").scrollIntoView();
            cy.get("#nk-graph-main").then(([paper]) =>
                cy
                    .get(`[data-testid='component:${component}']`)
                    .should("be.visible")
                    .drag("#nk-graph-main", { target: dropTargetBesideScenario(paper), force: true }),
            );
            cy.getNode(addedNodeName);
        };

        // Laying out between the steps keeps the scenario a compact column, so there is always room beside it
        // for the next component.
        dropComponent(/^sources$/i, "Event Generator", "Event Generator");
        dropComponent(/^base$/i, "Deduplication", "Deduplication");
        connectFreeOutputTo("Event Generator", "Deduplication");

        cy.layoutScenario();
        dropComponent(/^sinks$/i, "Dead End", "Dead End");
        connectFreeOutputTo("Deduplication", "Dead End");

        cy.layoutScenario();
        dropComponent(/^sinks$/i, "Dead End", "Dead End 1");
        connectFreeOutputTo("Deduplication", "Dead End 1");

        cy.wait("@validation");
        cy.layoutScenario();
        fillDeduplicationParameters();

        // The main edge is a named output like any other, so its link id ends with "passed" (EspNode/link.ts).
        cy.get('[model-id$="-passed"]').should("be.visible");
        cy.get('[model-id$="-passed"] .label').should("contain.text", "passed");
        cy.get('[model-id$="-rejected"]').should("be.visible");
        cy.get('[model-id$="-rejected"] .label').should("contain.text", "rejected");

        cy.get("[data-testid=graphPage]").matchImage({
            screenshotConfig: { blackout: ["[data-testid=SidePanel]"] },
        });

        // Both edges persist as named CustomNodeOutput entries, the main one included. Edges reference node
        // ids, not names, and both sinks display as "Dead End", so the sinks are resolved from `nodes` and
        // told apart by their edge names.
        const assertEdgesPersistAsNamedOutputs = () =>
            cy.get<string>("@processName").then((processName) =>
                cy
                    .request(`/api/processes/${processName}`)
                    .its("body.scenarioGraph")
                    .then(({ nodes, edges }: { nodes: PersistedNode[]; edges: PersistedEdge[] }) => {
                        const deadEndSinkIds = nodes.filter((node) => node.name.startsWith("Dead End")).map((node) => node.id);
                        expect(deadEndSinkIds, "the two Dead End sinks").to.have.length(2);

                        const mainEdge = edges.find((edge) => deadEndSinkIds.includes(edge.to) && edge.edgeType?.name === "passed");
                        const rejectedEdge = edges.find((edge) => deadEndSinkIds.includes(edge.to) && edge.edgeType?.name === "rejected");

                        expect(mainEdge, "main edge to a Dead End sink").to.exist;
                        expect(mainEdge.edgeType.type, "main edge is a named output").to.equal("CustomNodeOutput");
                        expect(rejectedEdge, "rejected edge to the other Dead End sink").to.exist;
                        expect(rejectedEdge.edgeType.type, "rejected edge is a named output").to.equal("CustomNodeOutput");
                        expect(rejectedEdge.to, "rejected edge points to the other sink").to.not.equal(mainEdge.to);
                    }),
            );

        cy.intercept("PUT", "/api/processes/*").as("save");
        cy.contains(/^save$/i)
            .should("be.enabled")
            .click();
        cy.contains(/^ok$/i).should("be.enabled").click();
        cy.wait("@save").its("response.statusCode").should("eq", 200);
        cy.contains(/^ok$/i).should("not.exist");

        assertEdgesPersistAsNamedOutputs();

        cy.visitProcess("@processName");
        cy.layoutScenario();

        cy.get('[model-id$="-passed"] .label').should("contain.text", "passed");
        cy.get('[model-id$="-rejected"] .label').should("contain.text", "rejected");
        assertEdgesPersistAsNamedOutputs();
    });
});
