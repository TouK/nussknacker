/* eslint-disable i18next/no-literal-string */
import React from "react";

import type { NodeOrPropertiesType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface DescriptionFieldProps {
    autoFocus?: boolean;
    defaultValue?: string;
    isEditMode?: boolean;
    node: NodeOrPropertiesType;
    readonly?: boolean;
    setProperty: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}

export function DescriptionField({
    autoFocus,
    defaultValue,
    isEditMode,
    node,
    readonly,
    setProperty,
    showValidation,
    errors,
}: DescriptionFieldProps): React.JSX.Element {
    return (
        <NodeField
            autoFocus={autoFocus}
            defaultValue={defaultValue}
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
