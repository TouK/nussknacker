import Field, { FieldType } from "./editors/field/Field";
import { getValidationErrorsForField } from "./editors/Validators";
import { get, isEmpty } from "lodash";
import React from "react";
import { useDiffMark } from "./PathsToMark";
import { NodeType, NodeValidationError, NodeOrPropertiesType } from "../../../types";
import { nodeInput, nodeInputWithError } from "./NodeDetailsContent/NodeTableStyled";
import { cx } from "@emotion/css";

type NodeFieldProps<N extends string, V> = {
    autoFocus?: boolean;
    defaultValue?: V;
    fieldLabel?: string;
    fieldName: N;
    fieldType: FieldType;
    isEditMode?: boolean;
    node: NodeOrPropertiesType;
    readonly?: boolean;
    renderFieldLabel: (paramName: string) => React.ReactNode;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showValidation?: boolean;
    errors: NodeValidationError[];
    description?: string;
};

export function NodeField<N extends string, V>({
    autoFocus,
    defaultValue,
    fieldLabel,
    fieldName,
    fieldType,
    isEditMode,
    node,
    readonly,
    renderFieldLabel,
    setProperty,
    showValidation,
    errors,
    description,
}: NodeFieldProps<N, V>): JSX.Element {
    const readOnly = !isEditMode || readonly;
    const value = get(node, fieldName, null) ?? defaultValue;
    const fieldErrors = getValidationErrorsForField(errors, fieldName);

    const className = cx({ [nodeInput]: true, [nodeInputWithError]: showValidation && !isEmpty(fieldErrors) });
    const onChange = (newValue) => setProperty(fieldName, newValue, defaultValue);
    const [isMarked] = useDiffMark();

    return (
        <Field
            type={fieldType}
            isMarked={isMarked(fieldName)}
            readOnly={readOnly}
            showValidation={showValidation}
            autoFocus={autoFocus}
            className={className}
            fieldErrors={fieldErrors}
            value={value}
            onChange={onChange}
            description={description}
        >
            {renderFieldLabel(fieldLabel)}
        </Field>
    );
}
