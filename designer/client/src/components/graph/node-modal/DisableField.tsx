import React from "react";

import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface DisableFieldProps {
    autoFocus?: boolean;
    defaultValue?: string;
    isEditMode?: boolean;
    node: NodeType;
    readonly?: boolean;
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
    setProperty,
    showValidation,
    errors,
}: DisableFieldProps): React.JSX.Element {
    return (
        <NodeField
            autoFocus={autoFocus}
            defaultValue={defaultValue}
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
