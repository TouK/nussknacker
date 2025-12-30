import type { NodesWithPositions } from "../../actions/nk/node";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import { initialTestCasesState } from "./testCases";

export const scenarioGraph: ScenarioGraph = {
    properties: {
        name: "Properties",
        additionalFields: {
            description: null,
            properties: {
                inputSchema: "{}",
                outputSchema: "{}",
                slug: "1819-jira-issue-example",
            },
            metaDataType: "RequestResponseMetaData",
            showDescription: false,
        },
    },
    nodes: [
        {
            id: "choice",
            expression: null,
            exprVal: null,
            additionalFields: {
                description: null,
                layoutData: {
                    x: 180,
                    y: 540,
                },
            },
            type: "Switch",
            label: "union",
        },
        {
            id: "variable 1",
            varName: "varName1",
            value: {
                language: "spel",
                expression: "'value'",
            },
            additionalFields: {
                description: null,
                layoutData: {
                    x: 0,
                    y: 720,
                },
            },
            type: "Variable",
            label: "variable",
        },
        {
            id: "variable 2",
            varName: "varName2",
            value: {
                language: "spel",
                expression: "'value'",
            },
            additionalFields: {
                description: null,
                layoutData: {
                    x: 360,
                    y: 720,
                },
            },
            type: "Variable",
            label: "variable",
        },
        {
            id: "union",
            outputVar: "outputVar",
            nodeType: "union",
            parameters: [],
            branchParameters: [
                {
                    branchId: "variable 1",
                    parameters: [
                        {
                            name: "Output expression",
                            expression: {
                                language: "spel",
                                expression: "1",
                            },
                        },
                    ],
                },
                {
                    branchId: "variable 2",
                    parameters: [
                        {
                            name: "Output expression",
                            expression: {
                                language: "spel",
                                expression: "2",
                            },
                        },
                    ],
                },
            ],
            additionalFields: {
                description: null,
                layoutData: {
                    x: 180,
                    y: 900,
                },
            },
            type: "Join",
            label: "union",
        },
    ],
    edges: [
        {
            from: "choice",
            to: "variable 1",
            edgeType: {
                condition: {
                    language: "spel",
                    expression: "true",
                },
                type: "NextSwitch",
            },
        },
        {
            from: "variable 1",
            to: "union",
            edgeType: null,
        },
        {
            from: "choice",
            to: "variable 2",
            edgeType: {
                condition: {
                    language: "spel",
                    expression: "true",
                },
                type: "NextSwitch",
            },
        },
        {
            from: "variable 2",
            to: "union",
            edgeType: null,
        },
        {
            from: "union",
            to: "",
            edgeType: null,
        },
    ],
    stickyNotes: [],
    testCases: initialTestCasesState,
};

export const nodesWithPositions: NodesWithPositions = [
    {
        node: {
            id: "variable 1",
            varName: "varName1",
            value: {
                language: "spel",
                expression: "'value'",
            },
            additionalFields: {
                description: null,
                layoutData: {
                    x: 0,
                    y: 720,
                },
            },
            type: "Variable",
            label: "variable",
        },
        position: {
            x: 350,
            y: 859,
        },
    },
    {
        node: {
            id: "variable 2",
            varName: "varName2",
            value: {
                language: "spel",
                expression: "'value'",
            },
            additionalFields: {
                description: null,
                layoutData: {
                    x: 360,
                    y: 720,
                },
            },
            type: "Variable",
            label: "variable",
        },
        position: {
            x: 710,
            y: 859,
        },
    },
    {
        node: {
            id: "union",
            outputVar: "outputVar",
            nodeType: "union",
            parameters: [],
            branchParameters: [
                {
                    branchId: "variable 1",
                    parameters: [
                        {
                            name: "Output expression",
                            expression: {
                                language: "spel",
                                expression: "1",
                            },
                        },
                    ],
                },
                {
                    branchId: "variable 2",
                    parameters: [
                        {
                            name: "Output expression",
                            expression: {
                                language: "spel",
                                expression: "2",
                            },
                        },
                    ],
                },
            ],
            additionalFields: {
                description: null,
                layoutData: {
                    x: 180,
                    y: 900,
                },
            },
            type: "Join",
            label: "union",
        },
        position: {
            x: 530,
            y: 1039,
        },
    },
];
