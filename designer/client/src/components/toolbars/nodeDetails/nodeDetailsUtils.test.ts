import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import { formatValue, getNodeSummaryItems } from "./nodeDetailsUtils";

// eslint-disable-next-line i18next/no-literal-string
const expr = (expression: string) => ({ expression });
const param = (name: string, expression: string) => ({ name, expression: expr(expression) });

describe("nodeDetailsUtils", () => {
    describe("formatValue", () => {
        describe("duration formatting", () => {
            it("should format seconds", () => {
                expect(formatValue("timeout", "T(java.time.Duration).parse('PT10S')")).toBe("10s");
            });

            it("should format minutes", () => {
                expect(formatValue("timeout", "T(java.time.Duration).parse('PT30M')")).toBe("30m");
            });

            it("should format hours and minutes", () => {
                expect(formatValue("timeout", "T(java.time.Duration).parse('PT1H30M')")).toBe("1h 30m");
            });

            it("should format Period with years and months", () => {
                expect(formatValue("period", "T(java.time.Period).parse('P1Y2M')")).toBe("1y 2mo");
            });

            it("should add 'every' prefix for schedule label", () => {
                expect(formatValue("schedule", "T(java.time.Duration).parse('PT10M')")).toBe("every 10m");
            });

            it("should not add 'every' prefix for non-schedule labels", () => {
                expect(formatValue("delay", "T(java.time.Duration).parse('PT10M')")).toBe("10m");
            });
        });

        describe("enum formatting", () => {
            it("should format SCREAMING_SNAKE_CASE enum", () => {
                expect(formatValue("trigger", "T(com.example.Trigger).ON_EACH_EVENT")).toBe("On each event");
            });

            it("should format PascalCase enum", () => {
                expect(formatValue("emitWhen", "T(com.example.Trigger).OnEvent")).toBe("On event");
            });

            it("should format multi-word PascalCase enum", () => {
                expect(formatValue("trigger", "T(com.example.Trigger).AfterWindowCloses")).toBe("After window closes");
            });

            it("should format single word PascalCase enum", () => {
                expect(formatValue("mode", "T(com.example.Mode).Enabled")).toBe("Enabled");
            });
        });

        describe("string literal stripping", () => {
            it("should strip surrounding single quotes", () => {
                expect(formatValue("topic", "'my-topic'")).toBe("my-topic");
            });

            it("should strip quotes from empty string literal", () => {
                expect(formatValue("topic", "''")).toBe("");
            });

            it("should not strip mismatched quotes", () => {
                expect(formatValue("x", "'hello")).toBe("'hello");
            });
        });

        describe("plain values", () => {
            it("should return SpEL variable unchanged", () => {
                expect(formatValue("key", "#input.id")).toBe("#input.id");
            });

            it("should return numeric literal unchanged", () => {
                expect(formatValue("count", "42")).toBe("42");
            });
        });
    });

    describe("getNodeSummaryItems", () => {
        describe("Filter", () => {
            it("should show condition and true/false paths with target node names", () => {
                const node = { type: "Filter", id: "f1", name: "f", expression: expr("#input.age > 18") } as unknown as NodeType;
                const nodes = [
                    { id: "f1", name: "f", type: "Filter" },
                    { id: "n1", name: "adult path", type: "Sink" },
                    { id: "n2", name: "rejected", type: "Sink" },
                ] as unknown as NodeType[];
                const edges: Edge[] = [
                    { from: "f1", to: "n1", edgeType: { type: EdgeKind.filterTrue } },
                    { from: "f1", to: "n2", edgeType: { type: EdgeKind.filterFalse } },
                ];
                expect(getNodeSummaryItems(node, undefined, { edges, nodes })).toEqual([
                    { label: "condition", value: "#input.age > 18" },
                    { label: "true", value: "-> adult path" },
                    { label: "false", value: "-> rejected" },
                ]);
            });

            it("should show only paths when expression is empty", () => {
                const node = { type: "Filter", id: "f1", name: "f", expression: expr("") } as unknown as NodeType;
                const nodes = [
                    { id: "n1", name: "pass", type: "Sink" },
                    { id: "n2", name: "drop", type: "Sink" },
                ] as unknown as NodeType[];
                const edges: Edge[] = [
                    { from: "f1", to: "n1", edgeType: { type: EdgeKind.filterTrue } },
                    { from: "f1", to: "n2", edgeType: { type: EdgeKind.filterFalse } },
                ];
                expect(getNodeSummaryItems(node, undefined, { edges, nodes })).toEqual([
                    { label: "true", value: "-> pass" },
                    { label: "false", value: "-> drop" },
                ]);
            });
        });

        describe("Switch", () => {
            it("should show conditions with target node names from edges", () => {
                const node = { type: "Switch", id: "s1", name: "s" } as unknown as NodeType;
                const nodes = [
                    { id: "s1", name: "s", type: "Switch" },
                    { id: "n1", name: "adult path", type: "Filter" },
                    { id: "n2", name: "default path", type: "Sink" },
                ] as unknown as NodeType[];
                const edges: Edge[] = [
                    {
                        from: "s1",
                        to: "n1",
                        edgeType: { type: EdgeKind.switchNext, condition: { language: "spel", expression: "#input.age > 18" } },
                    },
                    { from: "s1", to: "n2", edgeType: { type: EdgeKind.switchDefault } },
                ];
                expect(getNodeSummaryItems(node, undefined, { edges, nodes })).toEqual([
                    { label: "-> adult path", value: "#input.age > 18" },
                    { label: "-> default path", value: "default" },
                ]);
            });
        });

        describe("Variable", () => {
            it("should show output variable and value", () => {
                const node = { type: "Variable", id: "v1", name: "v", outputVar: "result", value: expr("1 + 2") } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "output variable", value: "#result" },
                    { label: "value", value: "1 + 2" },
                ]);
            });
        });

        describe("MapVariable", () => {
            it("should show only output variable", () => {
                const node = { type: "MapVariable", id: "mv1", name: "mv", outputVar: "result" } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "output variable", value: "#result" }]);
            });
        });

        describe("Source", () => {
            it("should show topic parameter for Kafka source", () => {
                const node = {
                    type: "Source",
                    id: "src1",
                    name: "src",
                    ref: { parameters: [param("topic", "'my-topic'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "topic", value: "my-topic" }]);
            });

            it("should show empty topic for Kafka source when not yet filled", () => {
                const node = {
                    type: "Source",
                    id: "src0",
                    name: "src",
                    ref: { parameters: [param("topic", "")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "topic", value: "" }]);
            });

            it("should show schedule for EventGenerator source", () => {
                const node = {
                    type: "Source",
                    id: "src2",
                    name: "eg",
                    ref: {
                        typ: "event-generator",
                        parameters: [param("schedule", "T(java.time.Duration).parse('PT10M')"), param("value", "123")],
                    },
                } as unknown as NodeType;
                const items = getNodeSummaryItems(node, undefined);
                expect(items).toEqual([{ label: "schedule", value: "every 10m" }]);
            });

            it("should show ref.typ as topic fallback for per-topic Kafka source", () => {
                const node = {
                    type: "Source",
                    id: "src3",
                    name: "src",
                    ref: { typ: "some.kafka.InputTopic", parameters: [] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "topic", value: "some.kafka.InputTopic" }]);
            });

            it("should show url for HTTP source", () => {
                const node = {
                    type: "Source",
                    id: "src4",
                    name: "src",
                    ref: { parameters: [param("url", "'https://api.example.com'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toContainEqual({ label: "url", value: "https://api.example.com" });
            });
        });

        describe("Sink", () => {
            it("should show topic parameter", () => {
                const node = {
                    type: "Sink",
                    id: "snk1",
                    name: "snk",
                    ref: { parameters: [param("topic", "'output-topic'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "topic", value: "output-topic" }]);
            });

            it("should show empty topic for Kafka sink when not yet filled", () => {
                const node = {
                    type: "Sink",
                    id: "snk0",
                    name: "snk",
                    ref: { parameters: [param("topic", "")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "topic", value: "" }]);
            });
        });

        describe("Enricher", () => {
            it("should show endpoint and output variable", () => {
                const node = {
                    type: "Enricher",
                    id: "e1",
                    name: "e",
                    output: "result",
                    service: { parameters: [param("endpoint", "'https://api.example.com/data'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "endpoint", value: "https://api.example.com/data" },
                    { label: "output variable", value: "#result" },
                ]);
            });

            it("should show url and output variable for http callback enricher", () => {
                const node = {
                    type: "Enricher",
                    id: "e2",
                    name: "http",
                    output: "response",
                    service: { parameters: [param("url", "'https://api.example.com/callback'"), param("method", "'POST'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "url", value: "https://api.example.com/callback" },
                    { label: "output variable", value: "#response" },
                ]);
            });

            it("should show empty url for http enricher when not yet filled", () => {
                const node = {
                    type: "Enricher",
                    id: "e3",
                    name: "http",
                    output: "response",
                    service: { parameters: [param("url", ""), param("method", "'POST'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "url", value: "" },
                    { label: "output variable", value: "#response" },
                ]);
            });

            it("should show service id and all params for OpenAPI enricher including empty", () => {
                const node = {
                    type: "Enricher",
                    id: "oa1",
                    name: "flights",
                    output: "flightData",
                    service: {
                        id: "flights-openAPI",
                        parameters: [param("departureAirport", "'WAW'"), param("arrivalAirport", "")],
                    },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "service", value: "flights-openAPI" },
                    { label: "departureAirport", value: "WAW" },
                    { label: "arrivalAirport", value: "" },
                    { label: "output variable", value: "#flightData" },
                ]);
            });
        });

        describe("Processor", () => {
            it("should show url for http callback processor", () => {
                const node = {
                    type: "Processor",
                    id: "p1",
                    name: "http",
                    service: { parameters: [param("url", "'https://api.example.com/notify'"), param("method", "'POST'")] },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([{ label: "url", value: "https://api.example.com/notify" }]);
            });
        });

        describe("CustomNode", () => {
            it("should show matchCondition and output variable for Decision Table", () => {
                const node = {
                    type: "CustomNode",
                    id: "dt1",
                    name: "dt",
                    nodeType: "decision-table",
                    outputVar: "dtResult",
                    parameters: [param("Match condition", "true"), param("Decision Table", "{'columns':[],'rows':[]}")],
                } as unknown as NodeType;
                const items = getNodeSummaryItems(node, undefined);
                expect(items).toContainEqual({ label: "Match condition", value: "true" });
                expect(items).toContainEqual({ label: "output variable", value: "#dtResult" });
                expect(items).not.toContainEqual(expect.objectContaining({ label: "Decision Table" }));
            });

            it("should show key, value and stateTimeout for Union Memo", () => {
                const node = {
                    type: "CustomNode",
                    id: "um1",
                    name: "um",
                    nodeType: "union-memo",
                    parameters: [
                        param("key", "#input.id"),
                        param("value", "#input.amount"),
                        param("stateTimeout", "T(java.time.Duration).parse('PT1H')"),
                    ],
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "key", value: "#input.id" },
                    { label: "value", value: "#input.amount" },
                    { label: "stateTimeout", value: "1h" },
                ]);
            });

            it("should show emitWhen, endSessionCondition, sessionTimeout, groupBy, key and outputVar for aggregate-session", () => {
                const node = {
                    type: "CustomNode",
                    id: "as1",
                    name: "as",
                    nodeType: "aggregate-session",
                    outputVar: "sessionResult",
                    parameters: [
                        param("emitWhen", "T(foo.SessionWindowTrigger).OnEvent"),
                        param("endSessionCondition", "#input.isLast"),
                        param("sessionTimeout", "T(java.time.Duration).parse('PT30M')"),
                        param("groupBy", "#input.userId"),
                        param("key", "#input.id"),
                        param("aggregator", "T(foo.Agg).Sum"),
                    ],
                } as unknown as NodeType;
                const items = getNodeSummaryItems(node, undefined);
                expect(items).toContainEqual({ label: "emitWhen", value: "On event" });
                expect(items).toContainEqual({ label: "endSessionCondition", value: "#input.isLast" });
                expect(items).toContainEqual({ label: "sessionTimeout", value: "30m" });
                expect(items).toContainEqual({ label: "groupBy", value: "#input.userId" });
                expect(items).toContainEqual({ label: "key", value: "#input.id" });
                expect(items).toContainEqual({ label: "output variable", value: "#sessionResult" });
                expect(items).not.toContainEqual(expect.objectContaining({ label: "aggregator" }));
            });

            it("should show emitWhen, windowLength and outputVar for aggregate-tumbling, hide aggregator", () => {
                const node = {
                    type: "CustomNode",
                    id: "ag1",
                    name: "ag",
                    nodeType: "aggregate-tumbling",
                    outputVar: "tumblingResult",
                    parameters: [
                        param("emitWhen", "T(foo.SessionWindowTrigger).OnEvent"),
                        param("windowLength", "T(java.time.Duration).parse('PT1H')"),
                        param("aggregator", "T(foo.Agg).Sum"),
                        param("aggregateBy", "#input.value"),
                    ],
                } as unknown as NodeType;
                const items = getNodeSummaryItems(node, undefined);
                expect(items).toContainEqual({ label: "emitWhen", value: "On event" });
                expect(items).toContainEqual({ label: "windowLength", value: "1h" });
                expect(items).toContainEqual({ label: "output variable", value: "#tumblingResult" });
                expect(items).not.toContainEqual(expect.objectContaining({ label: "aggregator" }));
                expect(items).not.toContainEqual(expect.objectContaining({ label: "aggregateBy" }));
            });

            it("should show emitWhen, emitWhenEventLeft, windowLength, groupBy, key and outputVar for aggregate-sliding", () => {
                const node = {
                    type: "CustomNode",
                    id: "ag3",
                    name: "sliding",
                    nodeType: "aggregate-sliding",
                    outputVar: "slidingResult",
                    parameters: [
                        param("emitWhen", "T(foo.SlidingTrigger).OnEvent"),
                        param("emitWhenEventLeft", "true"),
                        param("windowLength", "T(java.time.Duration).parse('PT1H')"),
                        param("groupBy", "#input.category"),
                        param("key", "#input.id"),
                        param("aggregator", "T(foo.Agg).Sum"),
                    ],
                } as unknown as NodeType;
                const items = getNodeSummaryItems(node, undefined);
                expect(items).toContainEqual({ label: "emitWhen", value: "On event" });
                expect(items).toContainEqual({ label: "emitWhenEventLeft", value: "true" });
                expect(items).toContainEqual({ label: "windowLength", value: "1h" });
                expect(items).toContainEqual({ label: "groupBy", value: "#input.category" });
                expect(items).toContainEqual({ label: "key", value: "#input.id" });
                expect(items).toContainEqual({ label: "output variable", value: "#slidingResult" });
                expect(items).not.toContainEqual(expect.objectContaining({ label: "aggregator" }));
            });

            it("should show key and delay for Delay node", () => {
                const node = {
                    type: "CustomNode",
                    id: "d1",
                    name: "delay",
                    nodeType: "delay",
                    parameters: [param("key", "#input.id"), param("delay", "T(java.time.Duration).parse('PT30S')")],
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "key", value: "#input.id" },
                    { label: "delay", value: "30s" },
                ]);
            });

            it("should include groupBy when present", () => {
                const node = {
                    type: "CustomNode",
                    id: "ag2",
                    name: "ag",
                    nodeType: "aggregate-sliding",
                    parameters: [
                        param("emitWhen", "T(foo.Trigger).OnEvent"),
                        param("groupBy", "#input.category"),
                        param("key", "#input.id"),
                    ],
                } as unknown as NodeType;
                const items = getNodeSummaryItems(node, undefined);
                expect(items).toContainEqual({ label: "groupBy", value: "#input.category" });
            });
        });

        describe("FragmentInput", () => {
            it("should show ref id and output variable names", () => {
                const node = {
                    type: "FragmentInput",
                    id: "fi1",
                    name: "fi",
                    ref: { id: "my-fragment", outputVariableNames: { out1: "result", out2: "extra" } },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "ref", value: "my-fragment" },
                    { label: "output.out1", value: "#result" },
                    { label: "output.out2", value: "#extra" },
                ]);
            });

            it("should show ref id, input parameters with values, and output variable names", () => {
                const node = {
                    type: "FragmentInput",
                    id: "fi2",
                    name: "fi",
                    ref: {
                        id: "empty fragment",
                        parameters: [param("a", "'hello'"), param("b", "#input.id"), param("c", "")],
                        outputVariableNames: { output: "empty_fragment_output" },
                    },
                } as unknown as NodeType;
                expect(getNodeSummaryItems(node, undefined)).toEqual([
                    { label: "ref", value: "empty fragment" },
                    { label: "a", value: "hello" },
                    { label: "b", value: "#input.id" },
                    { label: "output variable", value: "#empty_fragment_output" },
                ]);
            });
        });
    });
});
