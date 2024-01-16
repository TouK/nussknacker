import React from "react";
import { NodeType, NodeValidationError } from "../../../../types";
import { IdField } from "../IdField";

export function NodeDetailsFallback(props: {
    node: NodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    isEditMode?: boolean;
    showValidation?: boolean;
    errors: NodeValidationError[];
}): JSX.Element {
    return (
        <>
            <IdField {...props} errors={props.errors} />
            <span>Node type not known.</span>
            <pre>{JSON.stringify(props.node, null, 2)}</pre>
        </>
    );
}
