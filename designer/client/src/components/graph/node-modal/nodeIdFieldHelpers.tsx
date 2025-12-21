// wise decision to treat a name as an id forced me to do so.
import { createSelector } from "reselect";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
// now we have consistent id for validation, branch params etc
import type { NodeId, NodeType } from "../../../types/node";
import NodeUtils from "../NodeUtils";
import type { Validator } from "./editors/Validators";

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

export const getProcessNodesIds = createSelector(getScenarioGraph, (p) => NodeUtils.nodesFromScenarioGraph(p).map((n) => n[PROP_NAME]));

export function appendNodeIdPlaceholder(newValue: string) {
    return `${PLACEHOLDER_CHARACTER}${newValue}`;
}

export function cleanNodeIdPlaceholder(newValue: string) {
    return newValue.replace(PLACEHOLDER_CHARACTER, "");
}

export function hasNodeIdPlaceholder(newValue: string) {
    return newValue.includes(PLACEHOLDER_CHARACTER);
}

export function fixNodeIdValue(newValue: string, extraValidators: Validator[]) {
    let fixedValue = newValue;
    while (extraValidators.some((v) => !v.isValid(fixedValue))) {
        fixedValue = appendNodeIdPlaceholder(fixedValue);
    }
    return fixedValue;
}
