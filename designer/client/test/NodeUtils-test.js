import NodeUtils from "../src/components/graph/NodeUtils";

describe("edgeType retrieved", () => {
    it("should choose unused edge type", () => {
        expect(
            NodeUtils.getNextEdgeType(
                [{ from: "node1", edgeType: { type: "edge1" } }],
                { id: "node1", type: "Filter" },
                processDefinitionData,
            ),
        ).toEqual({ type: "edge2" });
    });

    it("should get edge types for node", () => {
        expect(
            NodeUtils.getEdgesAvailableForNode({ id: "node1", type: "FragmentInput", ref: { id: "sub1" } }, processDefinitionData),
        ).toEqual({
            componentId: "fragment-sub1",
            edges: [{ type: "edge3" }],
        });
        expect(NodeUtils.getEdgesAvailableForNode({ id: "node1", type: "Filter" }, processDefinitionData)).toEqual({
            componentId: "builtin-filter",
            edges: [{ type: "edge1" }, { type: "edge2" }],
        });
    });

    it("should get empty types for defaultNode", () => {
        expect(NodeUtils.getEdgesAvailableForNode({ id: "node1", type: "Variable" }, processDefinitionData)).toEqual({
            edges: [null],
            canChooseNodes: false,
        });
        expect(
            NodeUtils.getEdgesAvailableForNode({ id: "node1", type: "Processor", service: { id: "sub1" } }, processDefinitionData),
        ).toEqual({
            edges: [null],
            canChooseNodes: false,
        });
    });
});

describe("can make link", () => {
    it("cannot make link from non-last node to sink", () => {
        expect(
            NodeUtils.canMakeLink("source1", "sink", createSimpleProcess([{ from: "source1", to: "variable" }]), simpleProcessDefinition()),
        ).toEqual(false);
    });

    it("cannot connect from sink to any node", () => {
        expect(
            NodeUtils.canMakeLink(
                "sink",
                "variable",
                createSimpleProcess([{ from: "source1", to: "variable" }]),
                simpleProcessDefinition(),
            ),
        ).toEqual(false);
    });

    it("can connect from variable to sink", () => {
        expect(
            NodeUtils.canMakeLink(
                "variable",
                "sink",
                createSimpleProcess([{ from: "source1", to: "variable" }]),
                simpleProcessDefinition(),
            ),
        ).toEqual(true);
    });

    it("cannot connect to source", () => {
        expect(
            NodeUtils.canMakeLink(
                "variable",
                "source2",
                createSimpleProcess([{ from: "source1", to: "variable" }]),
                simpleProcessDefinition(),
            ),
        ).toEqual(false);
    });

    it("cannot make more than 2 links for Filter", () => {
        //filter has limit of two
        let baseEdges = [{ from: "source1", to: "filter1" }];
        expect(NodeUtils.canMakeLink("filter1", "sink", createSimpleProcess(baseEdges), simpleProcessDefinition())).toEqual(true);
        expect(
            NodeUtils.canMakeLink(
                "filter1",
                "variable",
                createSimpleProcess([...baseEdges, { from: "filter1", to: "sink" }]),
                simpleProcessDefinition(),
            ),
        ).toEqual(true);
        //creating 3'rd link is impossible
        expect(
            NodeUtils.canMakeLink(
                "filter1",
                "sink2",
                createSimpleProcess([...baseEdges, { from: "filter1", to: "sink" }, { from: "filter1", to: "variable" }]),
                simpleProcessDefinition(),
            ),
        ).toEqual(false);
    });

    it("can edit link if Filter if fully linked", () => {
        //filter has limit of two
        let baseEdges = [{ from: "source1", to: "filter1" }];
        let previousEdge = { from: "filter", to: "variable" };
        expect(NodeUtils.canMakeLink("filter1", "sink", createSimpleProcess(baseEdges), simpleProcessDefinition())).toEqual(true);
        expect(
            NodeUtils.canMakeLink(
                "filter1",
                "variable",
                createSimpleProcess([...baseEdges, { from: "filter1", to: "sink" }]),
                simpleProcessDefinition(),
            ),
        ).toEqual(true);
        //editing 2'rd link is possible
        expect(
            NodeUtils.canMakeLink(
                "filter1",
                "sink2",
                createSimpleProcess([...baseEdges, { from: "filter1", to: "sink" }, { from: "filter1", to: "variable" }]),
                simpleProcessDefinition(),
                previousEdge,
            ),
        ).toEqual(true);
    });
});

describe("isAvailable", () => {
    let processDefinitionData;
    let component;

    beforeAll(() => {
        processDefinitionData = {
            components: {
                "service-clientHttpService": {
                    parameters: [],
                    returnType: null,
                    icon: "/foo",
                    docsUrl: null,
                    outputParameters: null,
                },
            },
        };

        component = {
            service: {
                parameters: [
                    {
                        expression: {
                            expression: "'parameter'",
                            language: "spel",
                        },
                        name: "id",
                    },
                ],
                id: "clientHttpService",
            },
            id: "clientWithParameters",
            additionalFields: {
                description: "some description",
            },
            output: "output-changed",
            type: "Enricher",
        };
    });

    it("should be available", () => {
        const available = NodeUtils.isAvailable(component, processDefinitionData);

        expect(available).toBe(true);
    });

    it("should not be available for unknown node", () => {
        const unknownComponentModel = { ...component, service: { ...component.service, id: "unknown" } };

        const available = NodeUtils.isAvailable(unknownComponentModel, processDefinitionData);

        expect(available).toBe(false);
    });
});

const processDefinitionData = {
    edgesForNodes: [
        {
            componentId: "builtin-filter",
            edges: [{ type: "edge1" }, { type: "edge2" }],
        },
        {
            componentId: "fragment-sub1",
            edges: [{ type: "edge3" }],
        },
    ],
};

const simpleProcessDefinition = () => {
    return {
        edgesForNodes: [
            { componentId: "builtin-filter", edges: [{ type: "FilterTrue" }, { type: "FilterFalse" }], canChooseNodes: false },
            { componentId: "builtin-split", edges: [], canChooseNodes: true },
            {
                componentId: "builtin-choice",
                edges: [{ type: "NextSwitch", condition: { language: "spel", expression: "true" } }, { type: "SwitchDefault" }],
                canChooseNodes: true,
            },
        ],
    };
};

const createSimpleProcess = (edges) => ({
    properties: { additionalFields: { groups: [] } },
    nodes: [
        { type: "Source", id: "source1", ref: { typ: "csv-source", parameters: [] } },
        { type: "Source", id: "source2", ref: { typ: "csv-source", parameters: [] } },
        { type: "Filter", id: "filter1" },
        { type: "Variable", id: "variable", varName: "varName", value: { language: "spel", expression: "'value'" } },
        { type: "Sink", id: "sink", ref: { typ: "sendSms", parameters: [] } },
        { type: "Sink", id: "sink2", ref: { typ: "sendSms", parameters: [] } },
    ],
    edges: edges,
});
