import type { Edge } from "../../../types/edge";
import { EdgeKind } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import type { ProcessDefinitionData } from "../../../types/scenarioGraph";
import { ExpressionLang } from "./editors/expression/types";
import { adjustEdges } from "./NodeSwitcherUtils";

const dedupNode: NodeType = { id: "dedup1", name: "dedup", type: "CustomNode", nodeType: "deduplication" };
const plainNode: NodeType = { id: "proc1", name: "proc", type: "Processor", service: { id: "someService" } };
const filterNode: NodeType = { id: "filter1", name: "filter", type: "Filter" };
const switchNode: NodeType = { id: "switch1", name: "switch", type: "Switch" };
const fragmentNode: NodeType = {
    id: "frag1",
    name: "frag",
    type: "FragmentInput",
    ref: { id: "someFragment", typ: "someFragment", parameters: [], outputVariableNames: { output1: "output1", output2: "output2" } },
};

const processDefinitionDataWithDedup: ProcessDefinitionData = {
    edgesForNodes: [
        {
            componentId: "custom-deduplication",
            edges: [
                { type: EdgeKind.customNodeOutput, name: "passed" },
                { type: EdgeKind.customNodeOutput, name: "rejected" },
            ],
            canChooseNodes: false,
            isForInputDefinition: false,
        },
    ],
};

const processDefinitionDataWithSplit: ProcessDefinitionData = {
    edgesForNodes: [{ componentId: "builtin-split", edges: [], canChooseNodes: true, isForInputDefinition: false }],
};

const processDefinitionDataWithChoice: ProcessDefinitionData = {
    edgesForNodes: [
        {
            componentId: "builtin-choice",
            edges: [
                { type: EdgeKind.switchNext, condition: { language: ExpressionLang.SpEL, expression: "true" } },
                { type: EdgeKind.switchDefault },
            ],
            canChooseNodes: true,
            isForInputDefinition: false,
        },
    ],
};

describe("adjustEdges", () => {
    it("keeps a declared CustomNodeOutput edge and remaps an untyped edge onto the free named entry", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "sinkA", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "sinkB" },
        ];

        const result = adjustEdges(outputEdges, dedupNode, processDefinitionDataWithDedup);

        expect(result).toEqual([
            { from: "dedup1", to: "sinkA", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "sinkB", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ]);
    });

    it("keeps a declared edge in its slot and remaps a stale one in declaration order on a switch between multi-output components", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "oldSink", edgeType: { type: EdgeKind.customNodeOutput, name: "old" } },
            { from: "dedup1", to: "passedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ];

        const result = adjustEdges(outputEdges, dedupNode, processDefinitionDataWithDedup);

        expect(result).toEqual([
            { from: "dedup1", to: "oldSink", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "passedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ]);
    });

    it("keeps the main subtree when collapsing a multi-output node to a single-output component", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "rejectedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "mainSink", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ];

        const result = adjustEdges(outputEdges, plainNode, processDefinitionDataWithDedup, dedupNode);

        expect(result).toEqual([{ from: "dedup1", to: "mainSink" }]);
    });

    it("strips edgeType from an edge whose output is not declared for the edited node", () => {
        const outputEdges: Edge[] = [{ from: "proc1", to: "sink1", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } }];

        const result = adjustEdges(outputEdges, plainNode, processDefinitionDataWithDedup);

        expect(result).toEqual([{ from: "proc1", to: "sink1" }]);
    });

    it("drops the edge that does not fit the sole output of the edited node", () => {
        const outputEdges: Edge[] = [
            { from: "proc1", to: "mainSink" },
            { from: "proc1", to: "rejectedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
        ];

        const result = adjustEdges(outputEdges, plainNode, processDefinitionDataWithDedup);

        expect(result).toEqual([{ from: "proc1", to: "mainSink" }]);
    });

    it("maps the main output to FilterTrue and drops the additional custom output on a switch to Filter", () => {
        // `rejected` is deduplication-specific - it must not masquerade as a Filter branch, even though
        // the FilterFalse slot is free. Only the main continuation carries over.
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "rejectedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "mainSink", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ];

        const result = adjustEdges(outputEdges, filterNode, processDefinitionDataWithDedup, dedupNode);

        expect(result).toEqual([{ from: "dedup1", to: "mainSink", edgeType: { type: EdgeKind.filterTrue } }]);
    });

    it("drops the edge that does not fit a Filter slot instead of leaving it untyped", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "s1", edgeType: { type: EdgeKind.filterTrue } },
            { from: "dedup1", to: "s2", edgeType: { type: EdgeKind.filterFalse } },
            { from: "dedup1", to: "s3" },
        ];

        const result = adjustEdges(outputEdges, filterNode, processDefinitionDataWithDedup, dedupNode);

        expect(result).toEqual([
            { from: "dedup1", to: "s1", edgeType: { type: EdgeKind.filterTrue } },
            { from: "dedup1", to: "s2", edgeType: { type: EdgeKind.filterFalse } },
        ]);
    });

    it("drops an additional custom output instead of turning it into a switch case", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "mainSink", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
            { from: "dedup1", to: "rejectedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
        ];

        const result = adjustEdges(outputEdges, switchNode, processDefinitionDataWithDedup, dedupNode);

        expect(result).toEqual([
            {
                from: "dedup1",
                to: "mainSink",
                edgeType: { type: EdgeKind.switchNext, condition: { language: ExpressionLang.SpEL, expression: "true" } },
            },
        ]);
    });

    it("drops an additional custom output even when a fragment output name is free", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "rejectedSink", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "mainSink", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ];

        const result = adjustEdges(outputEdges, fragmentNode, processDefinitionDataWithDedup, dedupNode);

        expect(result).toEqual([{ from: "dedup1", to: "mainSink", edgeType: { type: EdgeKind.fragmentOutput, name: "output1" } }]);
    });

    it("keeps an existing switch case with its condition on a switch to Switch", () => {
        const condition = { language: ExpressionLang.SpEL, expression: "#input > 0" };
        const outputEdges: Edge[] = [
            { from: "switch0", to: "s1", edgeType: { type: EdgeKind.switchNext, condition } },
            { from: "switch0", to: "s2" },
        ];

        const result = adjustEdges(outputEdges, switchNode, processDefinitionDataWithDedup);

        expect(result).toEqual([
            { from: "switch0", to: "s1", edgeType: { type: EdgeKind.switchNext, condition } },
            {
                from: "switch0",
                to: "s2",
                edgeType: { type: EdgeKind.switchNext, condition: { language: ExpressionLang.SpEL, expression: "true" } },
            },
        ]);
    });

    it("keeps a fragment-output edge with a matching name and assigns the remaining name main-first", () => {
        const outputEdges: Edge[] = [
            { from: "dedup1", to: "s1", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
            { from: "dedup1", to: "s2", edgeType: { type: EdgeKind.fragmentOutput, name: "output2" } },
            { from: "dedup1", to: "s3", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
        ];

        const result = adjustEdges(outputEdges, fragmentNode, processDefinitionDataWithDedup, dedupNode);

        expect(result).toEqual([
            { from: "dedup1", to: "s2", edgeType: { type: EdgeKind.fragmentOutput, name: "output2" } },
            { from: "dedup1", to: "s3", edgeType: { type: EdgeKind.fragmentOutput, name: "output1" } },
        ]);
    });

    it("collapsing a Choice keeps the first branch in list order, not the one left on the default condition", () => {
        // A Choice's first available entry is the template for new branches, not its main output, so the branch
        // whose condition happens to be `true` must not outrank the one that comes first.
        const outputEdges: Edge[] = [
            {
                from: "switch1",
                to: "sinkA",
                edgeType: { type: EdgeKind.switchNext, condition: { language: ExpressionLang.SpEL, expression: "#input > 0" } },
            },
            {
                from: "switch1",
                to: "sinkB",
                edgeType: { type: EdgeKind.switchNext, condition: { language: ExpressionLang.SpEL, expression: "true" } },
            },
        ];

        const result = adjustEdges(outputEdges, plainNode, processDefinitionDataWithChoice, switchNode);

        expect(result).toEqual([{ from: "switch1", to: "sinkA" }]);
    });

    it("keeps every unnamed edge of a split-like node", () => {
        const splitNode: NodeType = { id: "split1", name: "split", type: "Split" };
        const outputEdges: Edge[] = [
            { from: "split1", to: "sink1" },
            { from: "split1", to: "sink2" },
        ];

        const result = adjustEdges(outputEdges, splitNode, processDefinitionDataWithSplit);

        expect(result).toEqual(outputEdges);
    });
});
