import { isEmpty } from "lodash";
import React, { useMemo, useState } from "react";
import { createSelector } from "reselect";

import { getScenarioGraph } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeOrPropertiesType, NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import NodeUtils from "../NodeUtils";
import Field, { FieldType } from "./editors/field/Field";
import { FieldLabelConsumer } from "./editors/RenderFieldLabel";
import { extendErrors, getValidationErrorsForField, uniqueScenarioValueValidator } from "./editors/Validators";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface IdFieldProps {
    isEditMode?: boolean;
    node: NodeOrPropertiesType;

    setProperty?: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

// wise decision to treat a name as an id forced me to do so.
// now we have consistent id for validation, branch params etc
const propName = `id`;
const FAKE_NAME_PROP_NAME = "$id";

export type EditedNode = NodeType & {
    [FAKE_NAME_PROP_NAME]?: string;
};

export function applyIdFromFakeName({ id, ...editedNode }: EditedNode): NodeType {
    const name = editedNode[FAKE_NAME_PROP_NAME];
    delete editedNode[FAKE_NAME_PROP_NAME];
    return { ...editedNode, id: name ?? id };
}

export const getProcessNodesIds = createSelector(getScenarioGraph, (p) => NodeUtils.nodesFromScenarioGraph(p).map((n) => n.id));

export function IdField({ isEditMode, node, setProperty, showValidation, errors }: IdFieldProps): React.JSX.Element {
    const nodes = useAppSelector(getProcessNodesIds);
    const [otherNodes] = useState(() => nodes.filter((n) => n !== node[propName]));

    const [isMarked] = useDiffMark();
    const value = useMemo(() => node[FAKE_NAME_PROP_NAME] ?? node[propName], [node]);
    const marked = useMemo(() => isMarked(FAKE_NAME_PROP_NAME) || isMarked(propName), [isMarked]);

    const isUniqueValueValidator = uniqueScenarioValueValidator(otherNodes);

    const fieldErrors = getValidationErrorsForField(
        isUniqueValueValidator ? extendErrors(errors, value, FAKE_NAME_PROP_NAME, [isUniqueValueValidator]) : errors,
        FAKE_NAME_PROP_NAME,
    );

    return (
        <Field
            type={FieldType.input}
            isMarked={marked}
            showValidation={showValidation}
            onChange={(newValue) => setProperty(FAKE_NAME_PROP_NAME, newValue.toString())}
            readOnly={!isEditMode}
            className={!showValidation || isEmpty(fieldErrors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
            fieldErrors={fieldErrors}
            value={value}
            autoFocus
        >
            <FieldLabelConsumer text="Name" />
        </Field>
    );
}
