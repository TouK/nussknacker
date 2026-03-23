import React, { useContext } from "react";

import { ConditionBuilderContext } from "../../conditionBuilder/ConditionBuilderContext";
import { BuilderIconButton } from "./node-action-buttons/StyledLoadingButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function ConditionBuilderFieldWrapper({ children, isEditMode }: FieldWrapperProps) {
    const conditionBuilderContext = useContext(ConditionBuilderContext);

    const inputAdornmentEnd =
        conditionBuilderContext && isEditMode ? (
            <BuilderIconButton onClick={() => conditionBuilderContext()} label="Condition Builder" />
        ) : undefined;

    return <>{React.cloneElement(children as React.ReactElement, { inputAdornmentEnd })}</>;
}
