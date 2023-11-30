import ProcessUtils from "../src/common/ProcessUtils";
import { reject } from "lodash";

const unknown = { display: "Unknown", params: [], type: "Unknown", refClazzName: "java.lang.Object" };

describe("process available variables finder", () => {
    it("should find available variables with its types in process at the beginning of the process", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, process)("processVariables");
        expect(availableVariables).toEqual({
            input: { refClazzName: "org.nussknacker.model.Transaction" },
        });
    });

    it("should find available variables with its types in process in the end of the process", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, process)("endEnriched");

        expect(availableVariables).toEqual({
            input: { refClazzName: "org.nussknacker.model.Transaction" },
            parsedTransaction: { refClazzName: "org.nussknacker.model.Transaction" },
            aggregateResult: { refClazzName: "java.lang.String" },
            processVariables: unknown, //fixme how to handle variableBuilder here?
            someVariableName: unknown,
        });
    });

    it("should find fragment parameters as variables with its types", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, fragment)("endEnriched");
        expect(availableVariables).toEqual({
            fragmentParam: { refClazzName: "java.lang.String" },
        });
    });

    it("should return only empty variables for dangling node", () => {
        const danglingNodeId = "someFilterNode";
        const newEdges = reject(process.edges, (edge) => {
            return edge.from == danglingNodeId || edge.to == danglingNodeId;
        });
        const processWithDanglingNode = { ...process, ...{ edges: newEdges } };

        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, processWithDanglingNode)("danglingNodeId");

        expect(availableVariables).toEqual({});
    });

    it("should use variables from validation results if exist", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, processWithVariableTypes)("variableNode");

        expect(availableVariables).toEqual({
            input: { refClazzName: "java.lang.String" },
            processVariables: { refClazzName: "java.util.Map", fields: { field1: { refClazzName: "java.lang.String" } } },
        });
    });

    it("should fallback to variables decoded from graph if typing via validation fails", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, processWithVariableTypes)("anonymousUserFilter");

        expect(availableVariables).toEqual({
            someVariableName: unknown,
            processVariables: unknown,
            input: { refClazzName: "org.nussknacker.model.Transaction" },
        });
    });

    it("add additional variables to node if defined", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, processWithVariableTypes)(
            "aggregateId",
            paramWithAdditionalVariables,
        );

        expect(availableVariables).toEqual({
            additional1: { refClazzName: "java.lang.String" },
            input: { refClazzName: "org.nussknacker.model.Transaction" },
            parsedTransaction: { refClazzName: "org.nussknacker.model.Transaction" },
            processVariables: unknown,
            someVariableName: unknown,
        });
    });

    it("hide variables in parameter if defined", () => {
        const availableVariables = ProcessUtils.findAvailableVariables(processDefinition, processWithVariableTypes)(
            "aggregateId",
            paramWithVariablesToHide,
        );

        expect(availableVariables).toEqual({});
    });
});

const paramWithAdditionalVariables = {
    name: "withAdditional",
    additionalVariables: { additional1: { refClazzName: "java.lang.String" } },
};

const paramWithVariablesToHide = {
    name: "withVariablesToHide",
    variablesToHide: ["input", "parsedTransaction", "processVariables", "someVariableName"],
};

const processDefinition = {
    services: {
        transactionParser: {
            parameters: [],
            returnType: { refClazzName: "org.nussknacker.model.Transaction" },
        },
    },
    sourceFactories: {
        "kafka-transaction": {
            parameters: [
                {
                    name: "Topic",
                    typ: { refClazzName: "java.lang.String" },
                },
            ],
            returnType: { refClazzName: "org.nussknacker.model.Transaction" },
        },
    },
    sinkFactories: {
        endTransaction: {
            parameters: [{ name: "Topic", typ: { refClazzName: "java.lang.String" } }],
            returnType: { refClazzName: "pl.touk.esp.engine.kafka.KafkaSinkFactory" },
        },
    },
    customStreamTransformers: {
        transactionAggregator: {
            parameters: [paramWithAdditionalVariables, paramWithVariablesToHide],
            returnType: { refClazzName: "java.lang.String" },
        },
    },
    exceptionHandlerFactory: {
        parameters: [{ name: "errorsTopic", typ: { refClazzName: "java.lang.String" } }],
        returnType: { refClazzName: "org.nussknacker.process.espExceptionHandlerFactory" },
    },
    typesInformation: [
        {
            clazzName: { refClazzName: "org.nussknacker.model.Transaction" },
        },
        {
            clazzName: { refClazzName: "pl.touk.nussknacker.model.Account" },
        },
        {
            clazzName: { refClazzName: "java.time.LocalDate" },
        },
    ],
};

const process = {
    id: "transactionStart",
    properties: { parallelism: 2 },
    nodes: [
        {
            type: "Source",
            id: "start",
            ref: { typ: "kafka-transaction", parameters: [{ name: "Topic", Value: "transaction.topic" }] },
        },
        {
            type: "VariableBuilder",
            id: "processVariables",
            varName: "processVariables",
            fields: [{ name: "processingStartTime", expression: { language: "spel", expression: "#now()" } }],
        },
        {
            type: "Variable",
            id: "variableNode",
            varName: "someVariableName",
            value: { language: "spel", expression: "'value'" },
        },
        {
            type: "Filter",
            id: "anonymousUserFilter",
            expression: { language: "spel", expression: "#input.PATH != 'Anonymous'" },
        },
        {
            type: "Enricher",
            id: "decodeHtml",
            service: {
                id: "transactionParser",
                parameters: [{ name: "transaction", expression: { language: "spel", expression: "#input" } }],
            },
            output: "parsedTransaction",
        },
        { type: "Filter", id: "someFilterNode", expression: { language: "spel", expression: "true" } },
        {
            type: "CustomNode",
            id: "aggregateId",
            outputVar: "aggregateResult",
            nodeType: "transactionAggregator",
            parameters: [{ name: "withAdditional", value: "''" }],
        },
        {
            type: "Sink",
            id: "endEnriched",
            ref: { typ: "transactionSink", parameters: [{ name: "Topic", Value: "transaction.errors" }] },
        },
    ],
    edges: [
        { from: "start", to: "processVariables" },
        { from: "processVariables", to: "variableNode" },
        { from: "variableNode", to: "anonymousUserFilter" },
        { from: "anonymousUserFilter", to: "decodeHtml" },
        { from: "decodeHtml", to: "someFilterNode" },
        { from: "someFilterNode", to: "aggregateId" },
        { from: "aggregateId", to: "endEnriched" },
    ],
    validationResult: { errors: { invalidNodes: {} } },
};

const processWithVariableTypes = {
    ...process,
    validationResult: {
        errors: { invalidNodes: {} },
        nodeResults: {
            start: {},
            processVariables: { variableTypes: { input: { refClazzName: "java.lang.String" } } },
            variableNode: {
                variableTypes: {
                    input: { refClazzName: "java.lang.String" },
                    processVariables: { refClazzName: "java.util.Map", fields: { field1: { refClazzName: "java.lang.String" } } },
                },
            },
        },
    },
};

const fragment = {
    id: "fragment1",
    properties: { parallelism: 2 },
    nodes: [
        {
            type: "FragmentInputDefinition",
            id: "start",
            parameters: [{ name: "fragmentParam", typ: { refClazzName: "java.lang.String" } }],
        },
        { type: "Filter", id: "filter1", expression: { language: "spel", expression: "#input.PATH != 'Anonymous'" } },
        {
            type: "Sink",
            id: "endEnriched",
            ref: { typ: "transactionSink", parameters: [{ name: "Topic", Value: "transaction.errors" }] },
        },
    ],
    edges: [
        { from: "start", to: "filter1" },
        { from: "filter1", to: "endEnriched" },
    ],
    validationResult: { errors: { invalidNodes: {} } },
};

describe("process utils", () => {
    const typingResult1 = { type: "java.lang.String", display: "String" };
    const typingResult2 = { type: "java.lang.Object", display: "Unknown" };
    it("should convert to readable type", () => {
        expect(ProcessUtils.humanReadableType(typingResult1)).toEqual("String");
        expect(ProcessUtils.humanReadableType(typingResult2)).toEqual("Unknown");
    });
});
