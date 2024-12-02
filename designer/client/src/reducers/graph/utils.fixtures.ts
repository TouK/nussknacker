import { GraphState } from "./types";
import { ProcessingMode } from "../../http/HttpService";
import { EdgeKind } from "../../types";
import { NodesWithPositions } from "../../actions/nk";

export const state: GraphState = {
    scenario: {
        name: "1819 jira issue example",
        processVersionId: 3,
        isLatestVersion: true,
        isArchived: false,
        isFragment: false,
        processingType: "request-response-embedded",
        processCategory: "RequestResponse",
        processingMode: ProcessingMode.requestResponse,
        engineSetupName: "Lite Embedded",
        modificationDate: "2024-10-03T08:36:52.856496Z",
        modifiedAt: "2024-10-03T08:36:52.856496Z",
        modifiedBy: "writer",
        createdAt: "2024-10-03T08:08:01.927407Z",
        createdBy: "writer",
        labels: [],
        scenarioGraph: {
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
                        type: EdgeKind.switchNext,
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
                        type: EdgeKind.switchNext,
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
        },
        state: {
            externalDeploymentId: null,
            status: {
                name: "NOT_DEPLOYED",
            },
            version: null,
            applicableActions: ["DEPLOY", "ARCHIVE", "RENAME"],
            allowedActions: ["DEPLOY", "ARCHIVE", "RENAME"],
            actionTooltips: {},
            icon: "/assets/states/not-deployed.svg",
            tooltip: "The scenario is not deployed.",
            description: "The scenario is not deployed.",
            startTime: null,
            attributes: null,
            errors: [],
        },
        validationResult: {
            errors: {
                invalidNodes: {},
                processPropertiesErrors: [],
                globalErrors: [
                    {
                        error: {
                            typ: "LooseNode",
                            message: "Loose node",
                            description: "Node choice is not connected to source, it cannot be saved properly",
                            fieldName: null,
                            errorType: "SaveNotAllowed",
                            details: null,
                        },
                        nodeIds: ["choice"],
                    },
                ],
            },
            warnings: {
                invalidNodes: {},
            },
            nodeResults: {
                "variable 3": {
                    variableTypes: {
                        outputVar: {
                            display: "Integer",
                            type: "TypedClass",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {
                        $expression: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                },
                "$edge-variable 1-union": {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                        varName: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                        varName1: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {},
                },
                request: {
                    variableTypes: {},
                    parameters: [],
                    typingInfo: {},
                },
                union: {
                    variableTypes: {},
                    parameters: [
                        {
                            name: "Output expression",
                            typ: {
                                display: "Unknown",
                                type: "Unknown",
                                refClazzName: "java.lang.Object",
                                params: [],
                            },
                            editor: {
                                type: "RawParameterEditor",
                            },
                            defaultValue: {
                                language: "spel",
                                expression: "",
                            },
                            additionalVariables: {},
                            variablesToHide: [],
                            branchParam: true,
                            hintText: null,
                            label: "Output expression",
                        },
                    ],
                    typingInfo: {
                        "Output expression-variable 1": {
                            value: 1,
                            display: "Integer(1)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                        "Output expression-variable 2": {
                            value: 2,
                            display: "Integer(2)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                    },
                },
                "$edge-variable 2-union": {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                        varName: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                        varName2: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {},
                },
                response: {
                    variableTypes: {
                        outputVar: {
                            display: "List[Integer(1)]",
                            type: "TypedClass",
                            refClazzName: "java.util.List",
                            params: [
                                {
                                    value: 1,
                                    display: "Integer(1)",
                                    type: "TypedObjectWithValue",
                                    refClazzName: "java.lang.Integer",
                                    params: [],
                                },
                            ],
                        },
                    },
                    parameters: [
                        {
                            name: "Raw editor",
                            typ: {
                                display: "Boolean",
                                type: "TypedClass",
                                refClazzName: "java.lang.Boolean",
                                params: [],
                            },
                            editor: {
                                type: "BoolParameterEditor",
                            },
                            defaultValue: {
                                language: "spel",
                                expression: "false",
                            },
                            additionalVariables: {},
                            variablesToHide: [],
                            branchParam: false,
                            hintText: null,
                            label: "Raw editor",
                        },
                        {
                            name: "Value",
                            typ: {
                                display: "Unknown",
                                type: "Unknown",
                                refClazzName: "java.lang.Object",
                                params: [],
                            },
                            editor: {
                                type: "RawParameterEditor",
                            },
                            defaultValue: {
                                language: "spel",
                                expression: "",
                            },
                            additionalVariables: {},
                            variablesToHide: [],
                            branchParam: false,
                            hintText: null,
                            label: "Value",
                        },
                    ],
                    typingInfo: {
                        "Raw editor": {
                            value: false,
                            display: "Boolean(false)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Boolean",
                            params: [],
                        },
                        Value: {
                            display: "Null",
                            type: "TypedNull",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                    },
                },
                "variable 1": {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                        varName: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {
                        $expression: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                },
                "union 1": {
                    variableTypes: {},
                    parameters: [
                        {
                            name: "Output expression",
                            typ: {
                                display: "Unknown",
                                type: "Unknown",
                                refClazzName: "java.lang.Object",
                                params: [],
                            },
                            editor: {
                                type: "RawParameterEditor",
                            },
                            defaultValue: {
                                language: "spel",
                                expression: "",
                            },
                            additionalVariables: {},
                            variablesToHide: [],
                            branchParam: true,
                            hintText: null,
                            label: "Output expression",
                        },
                    ],
                    typingInfo: {
                        "Output expression-variable 3": {
                            value: 1,
                            display: "Integer(1)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                    },
                },
                split: {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {},
                },
                variable: {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {
                        $expression: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                },
                choice: {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                        varName: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {
                        "variable 1": {
                            value: true,
                            display: "Boolean(true)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Boolean",
                            params: [],
                        },
                        "variable 2": {
                            value: true,
                            display: "Boolean(true)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Boolean",
                            params: [],
                        },
                    },
                },
                "$edge-variable 3-union 1": {
                    variableTypes: {
                        outputVar: {
                            display: "Integer",
                            type: "TypedClass",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                        varName: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {},
                },
                collect: {
                    variableTypes: {
                        outputVar: {
                            value: 1,
                            display: "Integer(1)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                    },
                    parameters: [
                        {
                            name: "Input expression",
                            typ: {
                                display: "Unknown",
                                type: "Unknown",
                                refClazzName: "java.lang.Object",
                                params: [],
                            },
                            editor: {
                                type: "RawParameterEditor",
                            },
                            defaultValue: {
                                language: "spel",
                                expression: "",
                            },
                            additionalVariables: {},
                            variablesToHide: [],
                            branchParam: false,
                            hintText: null,
                            label: "Input expression",
                        },
                    ],
                    typingInfo: {
                        "Input expression": {
                            value: 1,
                            display: "Integer(1)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.Integer",
                            params: [],
                        },
                    },
                },
                "variable 2": {
                    variableTypes: {
                        input: {
                            display: "Unknown",
                            type: "Unknown",
                            refClazzName: "java.lang.Object",
                            params: [],
                        },
                        varName: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                    parameters: null,
                    typingInfo: {
                        $expression: {
                            value: "value",
                            display: "String(value)",
                            type: "TypedObjectWithValue",
                            refClazzName: "java.lang.String",
                            params: [],
                        },
                    },
                },
            },
        },
        lastDeployedAction: null,
        lastAction: null,
        history: [
            {
                processVersionId: 3,
                createDate: "2024-10-03T08:36:52.856496Z",
                user: "writer",
                modelVersion: 4,
                actions: [],
            },
            {
                processVersionId: 2,
                createDate: "2024-10-03T08:14:04.647992Z",
                user: "writer",
                modelVersion: 4,
                actions: [],
            },
            {
                processVersionId: 1,
                createDate: "2024-10-03T08:08:02.059243Z",
                user: "writer",
                modelVersion: 4,
                actions: [],
            },
        ],
    },
    unsavedNewName: null,
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
    ],
    selectionState: [],
    scenarioLoading: false,
    testCapabilities: {
        canBeTested: false,
        canGenerateTestData: false,
        canTestWithForm: false,
    },
    testFormParameters: [],
    processCounts: {},
    testResults: null,
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
        },
        position: {
            x: 530,
            y: 1039,
        },
    },
];
