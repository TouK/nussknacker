import type { Edge, EdgeType } from "../../types/edge";
import { EdgeKind } from "../../types/edge";
import type { NodeType } from "../../types/node";
import type { ProcessDefinitionData } from "../../types/scenarioGraph";
import NodeUtils from "./NodeUtils";

const dedupNode: NodeType = { id: "dedup1", name: "dedup", type: "CustomNode", nodeType: "deduplication" };
const plainNode: NodeType = { id: "proc1", name: "proc", type: "Processor", service: { id: "someService" } };

const dedupEdges: EdgeType[] = [
    { type: EdgeKind.customNodeOutput, name: "passed" },
    { type: EdgeKind.customNodeOutput, name: "rejected" },
];

const definitionData: ProcessDefinitionData = {
    edgesForNodes: [
        {
            componentId: "custom-deduplication",
            edges: dedupEdges,
            canChooseNodes: false,
            isForInputDefinition: false,
        },
    ],
};

describe("getEdgesAvailableForNode", () => {
    it("returns the wire entries as-is, main entry included", () => {
        const result = NodeUtils.getEdgesAvailableForNode(dedupNode, definitionData);
        expect(result.edges).toEqual([
            { type: EdgeKind.customNodeOutput, name: "passed" },
            { type: EdgeKind.customNodeOutput, name: "rejected" },
        ]);
    });
});

describe("getNextEdgeType", () => {
    it("offers the main entry first when nothing is connected", () => {
        const next = NodeUtils.getNextEdgeType([], dedupNode, definitionData);
        expect(next).toEqual({ type: EdgeKind.customNodeOutput, name: "passed" });
    });

    it("recognizes a stored main edge by its name and offers the next entry", () => {
        const edges: Edge[] = [{ from: "dedup1", to: "sink1", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } }];
        const next = NodeUtils.getNextEdgeType(edges, dedupNode, definitionData);
        expect(next).toEqual({ type: EdgeKind.customNodeOutput, name: "rejected" });
    });

    it("offers nothing once both outputs are connected", () => {
        const edges: Edge[] = [
            { from: "dedup1", to: "sink1", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } },
            { from: "dedup1", to: "sink2", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } },
        ];
        expect(NodeUtils.getNextEdgeType(edges, dedupNode, definitionData)).toBeUndefined();
    });

    it("marks the sole unnamed entry of a plain node as used by an untyped edge", () => {
        const edges: Edge[] = [{ from: "proc1", to: "sink1" }];
        expect(NodeUtils.getNextEdgeType(edges, plainNode, definitionData)).toBeUndefined();
    });
});

describe("edgeLabel", () => {
    it("leaves an untyped edge unlabeled", () => {
        const edge: Edge = { from: "proc1", to: "sink1" };
        expect(NodeUtils.edgeLabel(edge)).toBe("");
    });

    it("labels named output edges from the edge itself", () => {
        const edge: Edge = { from: "dedup1", to: "sink1", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } };
        expect(NodeUtils.edgeLabel(edge)).toBe("rejected");
    });
});

describe("getFirstUnconnectedOutputEdge", () => {
    const availableEdges = dedupEdges;
    const freeMainEdge: Edge = { from: "dedup1", to: "", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } };
    const freeRejectedEdge: Edge = { from: "dedup1", to: "", edgeType: { type: EdgeKind.customNodeOutput, name: "rejected" } };

    it("matches a named request against the free edge of that output", () => {
        expect(
            NodeUtils.getFirstUnconnectedOutputEdge([freeMainEdge, freeRejectedEdge], availableEdges, {
                type: EdgeKind.customNodeOutput,
                name: "rejected",
            }),
        ).toBe(freeRejectedEdge);
    });

    it("finds nothing when the requested output has no free edge", () => {
        // A free edge of a different output must not be hijacked - the caller creates a new,
        // correctly typed edge instead and the dangling one stays where it was.
        expect(
            NodeUtils.getFirstUnconnectedOutputEdge([freeRejectedEdge], availableEdges, {
                type: EdgeKind.customNodeOutput,
                name: "passed",
            }),
        ).toBeUndefined();
    });

    it("falls back to the first free edge when no type is requested", () => {
        expect(NodeUtils.getFirstUnconnectedOutputEdge([freeRejectedEdge, freeMainEdge], availableEdges)).toBe(freeRejectedEdge);
    });

    it("finds nothing when every output edge is already connected", () => {
        const connected: Edge[] = [{ from: "dedup1", to: "sink1", edgeType: { type: EdgeKind.customNodeOutput, name: "passed" } }];
        expect(NodeUtils.getFirstUnconnectedOutputEdge(connected, availableEdges)).toBeUndefined();
    });
});
