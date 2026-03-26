import React from "react";

import { SpelExpressionBuilderComponent } from "../../spelExpressionBuilder/SpelExpressionBuilderComponent";
import { BaseBuilderFieldWrapper } from "./BaseBuilderFieldWrapper";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function SpelExpressionBuilderFieldWrapper(props: FieldWrapperProps) {
    return (
        <BaseBuilderFieldWrapper
            {...props}
            buttonLabel="Expression Builder"
            renderBuilder={({ node, onInsert, initialExpression, paramName, paramDef, open, onClose }) => (
                <SpelExpressionBuilderComponent
                    node={node}
                    onInsert={onInsert}
                    initialExpression={initialExpression === paramDef?.defaultValue?.expression ? undefined : initialExpression}
                    paramName={paramName}
                    targetTypeDisplay={paramDef?.typ?.display}
                    open={open}
                    onClose={onClose}
                />
            )}
        />
    );
}
