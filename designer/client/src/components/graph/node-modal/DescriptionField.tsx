/* eslint-disable i18next/no-literal-string */
import { NodeField } from "./NodeField";
import { FieldType } from "./editors/field/Field";
import React from "react";
import { Validator } from "./editors/Validators";
import { NodeData } from "../../../newTypes/displayableProcess";

interface DescriptionFieldProps {
    autoFocus?: boolean;
    defaultValue?: string;
    isEditMode?: boolean;
    node: NodeData;
    readonly?: boolean;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeData>(property: K, newValue: NodeData[K], defaultValue?: NodeData[K]) => void;
    showValidation?: boolean;
    validators?: Validator[];
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
    validators,
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
            validators={validators}
            fieldType={FieldType.plainTextarea}
            fieldLabel={"Description"}
            fieldProperty={"additionalFields.description"}
        />
    );
}
