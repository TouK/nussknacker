import React from "react";

import type { NodeType, NodeValidationError, StickyNoteNodeType } from "../../../types";
import Field, { FieldType } from "./editors/field/Field";
import { IdField } from "./IdField";

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
            onChange={undefined}
        >
            {renderFieldLabel(desc)}
        </Field>
    );

    const stickyNote = node as StickyNoteNodeType;
    return (
        <>
            <IdField
                isEditMode={isEditMode}
                showValidation={showValidation}
                node={stickyNote}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            {simpleField("Content", stickyNote.content)}
            {simpleField("Size", `Width: ${stickyNote.dimensions.width} Height: ${stickyNote.dimensions.height}`)}
            {simpleField("Color", stickyNote.color)}
        </>
    );
}
