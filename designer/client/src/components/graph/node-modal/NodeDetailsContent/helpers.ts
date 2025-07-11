import type { NodeType, Parameter } from "../../../../types";

export function findParameters(node: NodeType): Parameter[] {
    switch (node.type) {
        case "Source":
        case "Sink":
            return node.ref.parameters || [];
        case "Join":
        case "CustomNode":
            return node.parameters || [];
        case "Enricher":
        case "Processor":
            return node.service.parameters || [];
        default:
            return [];
    }
}
