import { v4 as uuidv4 } from "uuid";
import { nodeAdded, nodesWithEdgesAdded } from "../src/actions/nk/node";
import NodeUtils from "../src/components/graph/NodeUtils";
import { Scenario } from "../src/components/Process/types";
import rootReducer from "../src/reducers/index";
import { getLayout, getScenario, getScenarioGraph } from "../src/reducers/selectors/graph";
import type { Edge } from "../src/types/edge";
import type { NodeType } from "../src/types/node";

jest.mock("uuid");

let uuidCounter: number;
beforeEach(() => {
    uuidCounter = 0;
    (uuidv4 as jest.Mock).mockImplementation(() => `uuid-${++uuidCounter}`);
});

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
                name: "kafka-transaction",
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
                name: "filter",
                expression: {
                    language: "spel",
                    expression: "4 / (#input.length -5) >= 0",
                },
            },
            {
                type: "Enricher",
                id: "paramService",
                name: "paramService",
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
                name: "sendSms",
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
        testCases: {
            value: {
                id: "testCase1",
                name: "Test case 1",
                inputs: "{}",
                mocks: {},
                assertions: {
                    "Enricher ID": [
                        {
                            expected: { expression: "10", language: "spel" },
                            operator: "equals",
                            actual: { expression: "#input.value", language: "spel" },
                        },
                    ],
                },
            },
        },
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
    name: "Enricher ID",
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
            const scenarioGraph = getScenarioGraph(result);

            const addedNode = NodeUtils.getNodeByName("Enricher ID", scenarioGraph);
            expect(addedNode).toMatchSnapshot();
            expect(getLayout(result).find((n) => n.id === addedNode.id).position).toEqual(testPosition);
        });

        it("should add single node with unique name", () => {
            const result = reduceAll([nodeAdded({ ...testNode, id: "kafka-transaction", name: "kafka-transaction" }, testPosition)]);
            const scenarioGraph = getScenarioGraph(result);

            const addedNode = NodeUtils.getNodeByName("kafka-transaction 1", scenarioGraph);
            expect(addedNode).toMatchSnapshot();
            expect(getLayout(result).find((n) => n.id === addedNode.id).position).toEqual(testPosition);
        });

        it("should add multiple nodes with unique names", () => {
            const action = nodesWithEdgesAdded(
                [
                    {
                        node: { ...testNode, id: "kafka-transaction", name: "kafka-transaction" },
                        position: { x: 10, y: 20 },
                    },
                    {
                        node: { ...testNode, id: "filter", name: "filter" },
                        position: { x: 10, y: 20 },
                    },
                ],
                [],
            );
            const result = reduceAll([action, action]);
            const scenarioGraph = getScenarioGraph(result);

            expect(NodeUtils.getNodeByName("kafka-transaction (copy 1)", scenarioGraph)).toMatchSnapshot();
            expect(NodeUtils.getNodeByName("kafka-transaction (copy 2)", scenarioGraph)).toMatchSnapshot();
            expect(NodeUtils.getNodeByName("filter (copy 2)", scenarioGraph)).toMatchSnapshot();
        });

        it("should add nodes with edges", () => {
            const result = reduceAll([
                nodesWithEdgesAdded(
                    [
                        {
                            node: { ...testNode, id: "newNode", name: "newNode" },
                            position: testPosition,
                        },
                        {
                            node: { ...testNode, id: "kafka-transaction", name: "kafka-transaction" },
                            position: testPosition,
                        },
                    ],
                    [{ from: "newNode", to: "kafka-transaction" }],
                ),
            ]);

            const scenarioGraph = getScenarioGraph(result);
            const newNodeNode = NodeUtils.getNodeByName("newNode", scenarioGraph);
            const kafkaCopyNode = NodeUtils.getNodeByName("kafka-transaction (copy 1)", scenarioGraph);

            expect(newNodeNode).toMatchSnapshot();
            expect(kafkaCopyNode).toMatchSnapshot();

            expect(NodeUtils.getEdgeById(`${newNodeNode.id}-${kafkaCopyNode.id}`, scenarioGraph)).toEqual({
                from: newNodeNode.id,
                to: kafkaCopyNode.id,
            });
        });
    });
});
