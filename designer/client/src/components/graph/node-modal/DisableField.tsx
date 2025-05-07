import React from "react";

import type { NodeType, NodeValidationError } from "../../../types";
import { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";
import type { SetProperty } from "./NodeTypeDetailsContent";

interface DisableFieldProps {
    autoFocus?: boolean;
    defaultValue?: string;
    isEditMode?: boolean;
    node: NodeType;
    readonly?: boolean;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

export function DisableField({
    autoFocus,
    defaultValue,
    isEditMode,
    node,
    readonly,
    renderFieldLabel,
    setProperty,
    showValidation,
    errors,
}: DisableFieldProps): JSX.Element {
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
            fieldType={FieldType.checkbox}
            fieldLabel={"Disabled"}
            errors={errors}
            fieldName={"isDisabled"}
        />
    );
}
