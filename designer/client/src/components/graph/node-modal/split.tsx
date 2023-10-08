import { NodeType, NodeValidationError } from "../../../types";
import { NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { IdField } from "./IdField";
import { DescriptionField } from "./DescriptionField";
import React from "react";

export function Split({
    isEditMode,
    node,
    renderFieldLabel,
    setProperty,
    showValidation,
    fieldErrors,
}: {
    isEditMode?: boolean;
    node: NodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showValidation?: boolean;
    fieldErrors?: NodeValidationError[];
}): JSX.Element {
    return (
        <NodeTableBody>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={fieldErrors}
            />
            <DescriptionField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={node}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
            />
        </NodeTableBody>
    );
}
