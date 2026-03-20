import React from "react";

import { SpelExpressionPickerComponent } from "../../spelExpressionPicker/SpelExpressionPickerComponent";
import { BaseBuilderFieldWrapper } from "./BaseBuilderFieldWrapper";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function SpelExpressionPickerFieldWrapper(props: FieldWrapperProps) {
    return (
        <BaseBuilderFieldWrapper
            {...props}
            buttonLabel="Expression Picker"
            renderBuilder={({ node, onInsert, initialExpression, paramName, paramDef, open, onClose }) => (
                <SpelExpressionPickerComponent
                    node={node}
                    onInsert={onInsert}
                    initialExpression={initialExpression}
                    paramName={paramName}
                    targetTypeDisplay={paramDef?.typ?.display}
                    open={open}
                    onClose={onClose}
                />
            )}
        />
    );
}
