/* eslint-disable i18next/no-literal-string */
import { NodeField } from "./NodeField";
import { FieldType } from "./editors/field/Field";
import React from "react";
import { NodeType, NodeValidationError, NodeOrPropertiesType } from "../../../types";

interface DescriptionFieldProps {
    autoFocus?: boolean;
    defaultValue?: string;
    isEditMode?: boolean;
    node: NodeOrPropertiesType;
    readonly?: boolean;
    renderFieldLabel: (paramName: string) => React.ReactNode;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

export function DescriptionField({
    autoFocus,
    defaultValue,
    isEditMode,
    node,
    readonly,
    renderFieldLabel,
    setProperty,
    showValidation,
    errors,
}: DescriptionFieldProps): JSX.Element {
    return (
        <NodeField
            autoFocus={autoFocus}
            defaultValue={defaultValue}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            node={node}
            isEditMode={isEditMode}
            showValidation={showValidation}
            readonly={readonly}
            errors={errors}
            fieldType={FieldType.markdown}
            fieldLabel={"Description"}
            fieldName={"additionalFields.description"}
        />
    );
}
