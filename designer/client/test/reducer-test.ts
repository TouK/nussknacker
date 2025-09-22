import { nodeAdded, nodesWithEdgesAdded } from "../src/actions/nk";
import NodeUtils from "../src/components/graph/NodeUtils";
import { Scenario } from "../src/components/Process/types";
import rootReducer from "../src/reducers/index";
import { getLayout, getScenario, getScenarioGraph } from "../src/reducers/selectors/graph";
import type { Edge } from "../src/types/edge";
import type { NodeType } from "../src/types/node";

const baseProcessState: Scenario = {
    name: "xxx",
    isLatestVersion: true,
    isFragment: false,
    isArchived: false,
    scenarioGraph: {
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
        ] as NodeType[],
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
        ] as Edge[],
        stickyNotes: [],
        properties: null,
    },
    history: [],
    labels: [],
    engineSetupName: null,
    processCategory: null,
    modificationDate: null,
    createdAt: null,
    createdBy: null,
    modifiedAt: null,
    modifiedBy: null,
    processingMode: null,
    processingType: null,
    processVersionId: null,
    state: null,
    validationResult: null,
};

const baseState = rootReducer({}, { type: "@@INIT" } as any);

const baseStateWithProcess = rootReducer(baseState, {
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
            currentState = rootReducer(currentState, action);
        }
    };

    actions.forEach((action) => dispatch(action));

    return currentState;
};

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

describe("Store", () => {
    it("should have scenario with name", () => {
        expect(getScenario(baseStateWithProcess).name).toEqual(baseProcessState.name);
    });

    describe("Nodes added", () => {
        it("should add single node", () => {
            const result = reduceAll([nodeAdded(testNode, testPosition)]);

            expect(NodeUtils.getNodeById(testNode.id, getScenarioGraph(result))).toMatchSnapshot();
            expect(getLayout(result).find((n) => n.id === testNode.id).position).toEqual(testPosition);
        });

        it("should add single node with unique id", () => {
            const result = reduceAll([nodeAdded({ ...testNode, id: "kafka-transaction" }, testPosition)]);

            expect(NodeUtils.getNodeById("kafka-transaction 1", getScenarioGraph(result))).toMatchSnapshot();
            expect(getLayout(result).find((n) => n.id === "kafka-transaction 1").position).toEqual(testPosition);
        });

        it("should add multiple nodes with unique id", () => {
            const action = nodesWithEdgesAdded(
                [
                    {
                        node: { ...testNode, id: "kafka-transaction" },
                        position: { x: 10, y: 20 },
                    },
                    {
                        node: { ...testNode, id: "filter" },
                        position: { x: 10, y: 20 },
                    },
                ],
                [],
            );
            const result = reduceAll([action, action]);

            expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", getScenarioGraph(result))).toMatchSnapshot();
            expect(NodeUtils.getNodeById("kafka-transaction (copy 2)", getScenarioGraph(result))).toMatchSnapshot();
            expect(NodeUtils.getNodeById("filter (copy 2)", getScenarioGraph(result))).toMatchSnapshot();
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

            expect(NodeUtils.getNodeById("newNode", getScenarioGraph(result))).toMatchSnapshot();
            expect(NodeUtils.getNodeById("kafka-transaction (copy 1)", getScenarioGraph(result))).toMatchSnapshot();
            expect(NodeUtils.getEdgeById("newNode-kafka-transaction (copy 1)", getScenarioGraph(result))).toEqual({
                from: "newNode",
                to: "kafka-transaction (copy 1)",
            });
        });
    });
});
