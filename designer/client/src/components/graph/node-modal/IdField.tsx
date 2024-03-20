import { extendErrors, getValidationErrorsForField, uniqueScenarioValueValidator } from "./editors/Validators";
import Field, { FieldType } from "./editors/field/Field";
import React, { useMemo } from "react";
import { useDiffMark } from "./PathsToMark";
import { NodeType, NodeValidationError, UINodeType } from "../../../types";
import { useSelector } from "react-redux";
import { getProcessNodesIds } from "../../../reducers/selectors/graph";
import NodeUtils from "../NodeUtils";
import { isEmpty } from "lodash";

interface IdFieldProps {
    isEditMode?: boolean;
    node: UINodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty?: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

// wise decision to treat a name as an id forced me to do so.
// now we have consistent id for validation, branch params etc
const FAKE_NAME_PROP_NAME = "$name";

export function applyIdFromFakeName({ id, ...editedNode }: NodeType & { [FAKE_NAME_PROP_NAME]?: string }): NodeType {
    const name = editedNode[FAKE_NAME_PROP_NAME];
    delete editedNode[FAKE_NAME_PROP_NAME];
    return { ...editedNode, id: name ?? id };
}

export function IdField({ isEditMode, node, renderFieldLabel, setProperty, showValidation, errors }: IdFieldProps): JSX.Element {
    const nodes = useSelector(getProcessNodesIds);
    const otherNodes = useMemo(() => nodes.filter((n) => n !== node.id), [node.id, nodes]);

    const [isMarked] = useDiffMark();
    const propName = `id`;
    const errorFieldName = `$id`;
    const value = useMemo(() => node[FAKE_NAME_PROP_NAME] ?? node[propName], [node, propName]);
    const marked = useMemo(() => isMarked(FAKE_NAME_PROP_NAME) || isMarked(propName), [isMarked, propName]);

    const isUniqueValueValidator = !NodeUtils.nodeIsProperties(node) && uniqueScenarioValueValidator(otherNodes);

    const fieldErrors = getValidationErrorsForField(
        isUniqueValueValidator ? extendErrors(errors, value, errorFieldName, [isUniqueValueValidator]) : errors,
        errorFieldName,
    );

    return (
        <Field
            type={FieldType.input}
            isMarked={marked}
            showValidation={showValidation}
            onChange={(newValue) => setProperty(FAKE_NAME_PROP_NAME, newValue.toString())}
            readOnly={!isEditMode}
            className={!showValidation || isEmpty(fieldErrors) ? "node-input" : "node-input node-input-with-error"}
            fieldErrors={fieldErrors}
            value={value}
            autoFocus
        >
            {renderFieldLabel("Name")}
        </Field>
    );
}
