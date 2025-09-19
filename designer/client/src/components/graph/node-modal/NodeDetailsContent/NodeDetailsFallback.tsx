import React from "react";

import type { NodeType } from "../../../../types/node";
import type { NodeValidationError } from "../../../../types/validation";
import { IdField } from "../IdField";
import type { SetProperty } from "../useNodeTypeDetailsContentLogic";

export function NodeDetailsFallback(props: {
    node: NodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    isEditMode?: boolean;
    showValidation?: boolean;
    errors: NodeValidationError[];
}): JSX.Element {
    return (
        <>
            <IdField {...props} errors={props.errors} />
            <span>Node type not known.</span>
        </>
    );
}
