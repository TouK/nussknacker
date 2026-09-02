import type { Edge } from "../types/edge";
import { EdgeKind } from "../types/edge";

const EDITABLE_EDGES: string[] = [
    EdgeKind.switchNext,
    EdgeKind.switchDefault,
    EdgeKind.filterFalse,
    EdgeKind.filterTrue,
    EdgeKind.fragmentOutput,
    EdgeKind.customNodeOutput,
];

export function isEdgeEditable(edge?: Edge): boolean {
    const edgeType = edge?.edgeType?.type;
    return edgeType && EDITABLE_EDGES.includes(edgeType);
}
