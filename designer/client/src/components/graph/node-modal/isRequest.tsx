import type { NodeType } from "../../../types/node";

export function isRequest(node: NodeType) {
    return node.type === "Source" && node.ref?.typ === "request";
}

export function isResponse(node: NodeType) {
    return node.type === "Sink" && node.ref?.typ === "response";
}
