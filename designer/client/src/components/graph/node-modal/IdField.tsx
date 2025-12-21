import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { createSelector } from "reselect";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeId, NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import NodeUtils from "../NodeUtils";
import Field, { FieldType } from "./editors/field/Field";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import type { Validator } from "./editors/Validators";
import { extendErrors, getValidationErrorsForField, mandatoryValueValidator, uniqueScenarioValueValidator } from "./editors/Validators";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface IdFieldProps {
    isEditMode?: boolean;
    node: EditedNode;

    setProperty?: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

// wise decision to treat a name as an id forced me to do so.
// now we have consistent id for validation, branch params etc
const PROP_NAME = `id`;
const FAKE_NAME_PROP_NAME = "$id";
const PLACEHOLDER_CHARACTER = `‌`;

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

function fixNodeIdValue(newValue: string, extraValidators: Validator[]) {
    let fixedValue = newValue;
    while (extraValidators.some((v) => !v.isValid(fixedValue))) {
        fixedValue = appendNodeIdPlaceholder(fixedValue);
    }
    return fixedValue;
}

export function IdField({ isEditMode, node, setProperty, showValidation, errors }: IdFieldProps): React.JSX.Element {
    const nodes = useAppSelector(getProcessNodesIds);
    // stable node id before edits
    const [otherNodes] = useState(() => nodes.filter((n) => n !== node[PROP_NAME]));

    const [isMarked] = useDiffMark();
    const value = useMemo(() => getCurrentEditedId(node), [node]);
    const marked = useMemo(() => isMarked(FAKE_NAME_PROP_NAME) || isMarked(PROP_NAME), [isMarked]);

    const extraValidators = useMemo(() => {
        return [uniqueScenarioValueValidator(otherNodes), mandatoryValueValidator];
    }, [otherNodes]);

    const [internalValue, setInternalValue] = useState(value);

    const fieldErrors = useMemo(
        () => getValidationErrorsForField(extendErrors(errors, internalValue, FAKE_NAME_PROP_NAME, extraValidators), FAKE_NAME_PROP_NAME),
        [errors, extraValidators, internalValue],
    );

    useEffect(() => {
        setInternalValue(value.replace(PLACEHOLDER_CHARACTER, ""));
    }, [value]);

    const onChange = useCallback(
        (newValue: string) => {
            setInternalValue(cleanNodeIdPlaceholder(newValue));
            const fixedValue = fixNodeIdValue(newValue, extraValidators);
            setProperty(FAKE_NAME_PROP_NAME, fixedValue);
        },
        [extraValidators, setProperty],
    );

    return (
        <Field
            type={FieldType.input}
            isMarked={marked}
            showValidation={showValidation}
            onChange={onChange}
            readOnly={!isEditMode}
            className={!showValidation || isEmpty(fieldErrors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
            fieldErrors={fieldErrors}
            value={internalValue}
            autoFocus
        >
            <FieldLabelConsumer text="Name" />
        </Field>
    );
}
