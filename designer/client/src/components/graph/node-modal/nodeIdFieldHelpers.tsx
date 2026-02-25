import type { NodeId, NodeType } from "../../../types/node";
import type { Validator } from "./editors/Validators";

// node id is a stable UUID; name is the user-editable label
export const PROP_NAME = `name`;
export const FAKE_NAME_PROP_NAME = "$name";
export const PLACEHOLDER_CHARACTER = `‌`;
export type EditedNode = NodeType & {
    [FAKE_NAME_PROP_NAME]?: string;
};

function isEditingNodeId(node: EditedNode | NodeType): node is EditedNode {
    return FAKE_NAME_PROP_NAME in node;
}

export function applyIdFromFakeName(node: EditedNode): NodeType {
    if (!isEditingNodeId(node)) return node;
    const { [FAKE_NAME_PROP_NAME]: editedName, ...rest } = node;
    const newName = editedName ?? node[PROP_NAME];
    return { ...rest, [PROP_NAME]: newName };
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
