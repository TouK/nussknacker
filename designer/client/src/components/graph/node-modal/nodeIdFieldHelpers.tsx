import type { NodeId, NodeType } from "../../../types/node";
import type { Validator } from "./editors/Validators";

// wise decision to treat a name as an id forced me to do so.
// now we have consistent id for validation, branch params etc
export const PROP_NAME = `id`;
export const FAKE_NAME_PROP_NAME = "$id";
export const PLACEHOLDER_CHARACTER = `‌`;
export type EditedNode = NodeType & {
    [FAKE_NAME_PROP_NAME]?: string;
};

function isEditingNodeId(node: EditedNode | NodeType): node is EditedNode {
    return FAKE_NAME_PROP_NAME in node;
}

export function applyIdFromFakeName(node: EditedNode): NodeType {
    if (!isEditingNodeId(node)) return node;
    const { [FAKE_NAME_PROP_NAME]: name, ...rest } = node;
    return { ...rest, [PROP_NAME]: name ?? node[PROP_NAME] };
}

export function getCurrentEditedId(node: EditedNode): NodeId {
    return isEditingNodeId(node) ? node[FAKE_NAME_PROP_NAME] : node[PROP_NAME];
}

export function appendNodeIdPlaceholder(nodeId: string) {
    return `${PLACEHOLDER_CHARACTER}${nodeId}`;
}

export function cleanNodeIdPlaceholder(nodeId: string) {
    return nodeId.replace(PLACEHOLDER_CHARACTER, "");
}

export function hasNodeIdPlaceholder(nodeId: string) {
    return nodeId.includes(PLACEHOLDER_CHARACTER);
}

export function fixNodeIdValue(nodeId: string, extraValidators: Validator[]) {
    let fixedValue = nodeId;
    while (extraValidators.some((v) => !v.isValid(fixedValue))) {
        fixedValue = appendNodeIdPlaceholder(fixedValue);
    }
    return fixedValue;
}
