import React from "react";

import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DescriptionField } from "./DescriptionField";
import { NameField } from "./NameField";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export function Split({
    isEditMode,
    node,

    setProperty,
    showValidation,
    errors,
}: {
    isEditMode?: boolean;
    node: NodeType;

    setProperty: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}): React.JSX.Element {
    return (
        <>
            <NameField isEditMode={isEditMode} showValidation={showValidation} node={node} setProperty={setProperty} errors={errors} />
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
