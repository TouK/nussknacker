import { prepareNewNodesWithLayout } from "./utils";
import { nodesWithPositions, state } from "./utils.fixtures";

describe("GraphUtils prepareNewNodesWithLayout", () => {
    it("should update union output expression parameter with an updated node name when new unique node ids created", () => {
        const expected = {
            layout: [
                {
                    id: "choice",
                    position: {
                        x: 180,
                        y: 540,
                    },
                },
                {
                    id: "variable 1",
                    position: {
                        x: 0,
                        y: 720,
                    },
                },
                {
                    id: "variable 2",
                    position: {
                        x: 360,
                        y: 720,
                    },
                },
                {
                    id: "union",
                    position: {
                        x: 180,
                        y: 900,
                    },
                },
                {
                    id: "variable 1 (copy 1)",
                    position: {
                        x: 350,
                        y: 859,
                    },
                },
                {
                    id: "variable 2 (copy 1)",
                    position: {
                        x: 710,
                        y: 859,
                    },
                },
                {
                    id: "union (copy 1)",
                    position: {
                        x: 530,
                        y: 1039,
                    },
                },
            ],
            nodes: [
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 180,
                            y: 540,
                        },
                    },
                    exprVal: null,
                    expression: null,
                    id: "choice",
                    type: "Switch",
                },
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 0,
                            y: 720,
                        },
                    },
                    id: "variable 1",
                    type: "Variable",
                    value: {
                        expression: "'value'",
                        language: "spel",
                    },
                    varName: "varName1",
                },
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 360,
                            y: 720,
                        },
                    },
                    id: "variable 2",
                    type: "Variable",
                    value: {
                        expression: "'value'",
                        language: "spel",
                    },
                    varName: "varName2",
                },
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 180,
                            y: 900,
                        },
                    },
                    branchParameters: [
                        {
                            branchId: "variable 1",
                            parameters: [
                                {
                                    expression: {
                                        expression: "1",
                                        language: "spel",
                                    },
                                    name: "Output expression",
                                },
                            ],
                        },
                        {
                            branchId: "variable 2",
                            parameters: [
                                {
                                    expression: {
                                        expression: "2",
                                        language: "spel",
                                    },
                                    name: "Output expression",
                                },
                            ],
                        },
                    ],
                    id: "union",
                    nodeType: "union",
                    outputVar: "outputVar",
                    parameters: [],
                    type: "Join",
                },
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 0,
                            y: 720,
                        },
                    },
                    id: "variable 1 (copy 1)",
                    type: "Variable",
                    value: {
                        expression: "'value'",
                        language: "spel",
                    },
                    varName: "varName1",
                },
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 360,
                            y: 720,
                        },
                    },
                    id: "variable 2 (copy 1)",
                    type: "Variable",
                    value: {
                        expression: "'value'",
                        language: "spel",
                    },
                    varName: "varName2",
                },
                {
                    additionalFields: {
                        description: null,
                        layoutData: {
                            x: 180,
                            y: 900,
                        },
                    },
                    branchParameters: [
                        {
                            branchId: "variable 1 (copy 1)",
                            parameters: [
                                {
                                    expression: {
                                        expression: "1",
                                        language: "spel",
                                    },
                                    name: "Output expression",
                                },
                            ],
                        },
                        {
                            branchId: "variable 2 (copy 1)",
                            parameters: [
                                {
                                    expression: {
                                        expression: "2",
                                        language: "spel",
                                    },
                                    name: "Output expression",
                                },
                            ],
                        },
                    ],
                    id: "union (copy 1)",
                    nodeType: "union",
                    outputVar: "outputVar",
                    parameters: [],
                    type: "Join",
                },
            ],
            uniqueIds: ["variable 1 (copy 1)", "variable 2 (copy 1)", "union (copy 1)"],
        };

        expect(prepareNewNodesWithLayout(state, nodesWithPositions, true)).toEqual(expected);
    });
});
