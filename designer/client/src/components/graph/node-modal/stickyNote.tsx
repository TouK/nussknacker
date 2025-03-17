import { NodeType, NodeValidationError, StickyNoteNodeType } from "../../../types";
import { IdField } from "./IdField";
import React from "react";
import Field, { FieldType } from "./editors/field/Field";

export function StickyNote({
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
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showValidation?: boolean;
    errors: NodeValidationError[];
}): JSX.Element {
    const simpleField = (desc: string, value: string) => (
        <Field
            type={FieldType.input}
            readOnly={true}
            value={value}
            description={desc}
            isMarked={false}
            showValidation={false}
            autoFocus={false}
            className={value}
            fieldErrors={[]}
            onChange={{}}
        >
            {renderFieldLabel(desc)}
        </Field>
    );

    const stickyNode = node as StickyNoteNodeType;
    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={stickyNode}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            {simpleField("Content", stickyNode.content)}
            {simpleField("Size", `Width: ${stickyNode.dimensions.width} Height: ${stickyNode.dimensions.height}`)}
            {simpleField("Color", stickyNode.color)}
        </>
    );
}
