import { nodeAdded, nodesWithEdgesAdded } from "../src/actions/nk";
import NodeUtils from "../src/components/graph/NodeUtils";
import reducer from "../src/reducers/index";

const baseProcessState = {
    name: "DEFGH",
    isLatestVersion: true,
    processCategory: "Category1",
    modificationDate: "2017-02-14T11:16:56.686",
    createdAt: "2017-02-14T11:16:56.686",
    createdBy: "admin",
    tags: [],
    currentlyDeployedAt: ["test"],
    scenarioGraph: {
        properties: {
            parallelism: 3,
            additionalFields: {
                groups: [
                    {
                        id: "acdc",
                        nodes: ["kafka-transaction", "filter"],
                    },
                ],
            },
        },
        nodes: [
            {
                type: "Source",
                id: "kafka-transaction",
                ref: {
                    typ: "kafka-transaction",
                    parameters: [],
                },
                additionalFields: {
                    description: "asdfasdfłóóódźźźasdfsdfasdfasdfasdfasdf",
                },
            },
            {
                type: "Filter",
                id: "filter",
                expression: {
                    language: "spel",
                    expression: "4 / (#input.length -5) >= 0",
                },
            },
            {
                type: "Enricher",
                id: "paramService",
                service: {
                    id: "paramService",
                    parameters: [
                        {
                            name: "param",
                            expression: {
                                language: "spel",
                                expression: "'3434'",
                            },
                        },
                    ],
                },
                output: "output",
            },
            {
                type: "Sink",
                id: "sendSms",
                ref: {
                    typ: "sendSms",
                    parameters: [],
                },
            },
        ],
        edges: [
            {
                from: "kafka-transaction",
                to: "filter",
            },
            {
                from: "filter",
                to: "paramService",
                edgeType: {
                    type: "FilterTrue",
                },
            },
            {
                from: "paramService",
                to: "sendSms",
            },
        ],
    },
    validationResult: {
        errors: {
            invalidNodes: {},
            processPropertiesErrors: [],
            globalErrors: [],
        },
    },
    state: {},
};

const baseState = reducer(
    {},
    {
        type: "@@INIT",
    },
);

const baseStateWithProcess = reducer(baseState, {
    type: "DISPLAY_PROCESS",
    scenario: baseProcessState,
});

const reduceAll = (actions) => {
    let currentState = baseStateWithProcess;
    const getState = () => currentState;

    const dispatch = (action) => {
        if (typeof action === "function") {
            action(dispatch, getState);
        } else {
            currentState = reducer(currentState, action);
        }
    };

    actions.forEach((action) => dispatch(action));

    return currentState;
};

describe("Reducer suite", () => {
    it("Display process", () => {
        expect(baseStateWithProcess.graphReducer.scenario.name).toEqual(baseProcessState.name);
    });
});

const testNode = {
    type: "Enricher",
    id: "Enricher ID",
    service: {
        id: "paramService",
        parameters: [
            {
                name: "param",
                expression: {
                    language: "spel",
                    expression: "'3434'",
                },
            },
        ],
    },
    output: "output",
};

const testPosition = { x: 10, y: 20 };

describe("Nodes added", () => {
    it("should add single node", () => {
        const result = reduceAll([nodeAdded(testNode, testPosition)]);

        expect(NodeUtils.getNodeById(testNode.id, result.graphReducer.scenario.scenarioGraph)).toMatchSnapshot();
        expect(result.graphReducer.layout.find((n) => n.id === testNode.id).position).toEqual(testPosition);
    });

    it("should add single node with unique id", () => {
        const result = reduceAll([nodeAdded({ ...testNode, id: "kafka-transaction" }, testPosition)]);

        expect(NodeUtils.getNodeById("kafka-transaction 1", result.graphReducer.scenario.scenarioGraph)).toMatchSnapshot();
        expect(result.graphReducer.layout.find((n) => n.id === "kafka-transaction 1").position).toEqual(testPosition);
    });

    it("should add multiple nodes with unique id", () => {
        const result = reduceAll([
            nodesWithEdgesAdded(
                [
                    {
                        node: { ...testNode, id: "kafka-transaction" },
                        position: testPosition,
                    },
                    {
                        node: { ...testNode, id: "kafka-transaction" },
                        position: testPosition,
                    },
                ],
                [],
            ),
        ]);

        expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", result.graphReducer.scenario.scenarioGraph)).toMatchSnapshot();
        expect(NodeUtils.getNodeById("kafka-transaction (copy 2)", result.graphReducer.scenario.scenarioGraph)).toMatchSnapshot();
    });

    it("should add nodes with edges", () => {
        const result = reduceAll([
            nodesWithEdgesAdded(
                [
                    {
                        node: { ...testNode, id: "newNode" },
                        position: testPosition,
                    },
                    {
                        node: { ...testNode, id: "kafka-transaction" },
                        position: testPosition,
                    },
                ],
                [{ from: "newNode", to: "kafka-transaction" }],
            ),
        ]);

        expect(NodeUtils.getNodeById("newNode", result.graphReducer.scenario.scenarioGraph)).toMatchSnapshot();
        expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", result.graphReducer.scenario.scenarioGraph)).toMatchSnapshot();
        expect(NodeUtils.getEdgeById("newNode-kafka-transaction (copy 1)", result.graphReducer.scenario.scenarioGraph)).toEqual({
            from: "newNode",
            to: "kafka-transaction (copy 1)",
        });
    });
});
