import type { NodeType, Parameter } from "../../../../types";

export function serviceParameters(editedNode: NodeType): Parameter[] {
    return editedNode.service.parameters || [];
}
