import React from "react";

import type { NodeType, NodeValidationError } from "../../../types";
import { DescriptionField } from "./DescriptionField";
import { IdField } from "./IdField";
import type { SetProperty } from "./NodeTypeDetailsContent";

export function Split({
    isEditMode,
    node,
    renderFieldLabel,
    setProperty,
    showValidation,
    errors,
}: {
    isEditMode?: boolean;
    node: NodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showValidation?: boolean;
    errors: NodeValidationError[];
}): JSX.Element {
    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
