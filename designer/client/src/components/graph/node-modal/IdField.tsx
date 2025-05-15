import { isEmpty } from "lodash";
import React, { useMemo, useState } from "react";
import { useSelector } from "react-redux";

import { getProcessNodesIds } from "../../../reducers/selectors/graph";
import type { NodeOrPropertiesType, NodeType, NodeValidationError } from "../../../types";
import Field, { FieldType } from "./editors/field/Field";
import { extendErrors, getValidationErrorsForField, uniqueScenarioValueValidator } from "./editors/Validators";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import { useDiffMark } from "./PathsToMark";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface IdFieldProps {
    isEditMode?: boolean;
    node: NodeOrPropertiesType;
    renderFieldLabel: (paramName: string) => React.ReactNode;
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

export function IdField({ isEditMode, node, renderFieldLabel, setProperty, showValidation, errors }: IdFieldProps): JSX.Element {
    const nodes = useSelector(getProcessNodesIds);
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
            {renderFieldLabel("Name")}
        </Field>
    );
}
