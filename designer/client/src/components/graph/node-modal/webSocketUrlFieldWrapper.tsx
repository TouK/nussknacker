import React from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import { FieldAddons } from "./fieldAddons";
import { TestConnectionButton } from "./node-action-buttons/TestConnectionButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function WebSocketUrlFieldWrapper({ children, node, isEditMode, showValidation, fieldErrors }: FieldWrapperProps) {
    const [showTestConnectionButton] = useUserSettings("node.showTestConnectionButton");

    if (!isEditMode) return <>{children}</>;
    if (!showTestConnectionButton) return <>{children}</>;

    return (
        <>
            {children}
            <FieldAddons hasError={showValidation && fieldErrors.length > 0}>
                <TestConnectionButton node={node} disabled={!isEditMode} />
            </FieldAddons>
        </>
    );
}
