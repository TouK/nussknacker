import type { NodeType } from "../../../types/node";

export function isAggregate(node: NodeType) {
    return ["aggregate-session", "aggregate-sliding", "aggregate-tumbling"].includes(node.nodeType);
}
