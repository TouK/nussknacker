import React from "react";

import type { NodeType } from "../../../../types/node";
import type { NodeValidationError } from "../../../../types/validation";
import { NameField } from "../NameField";
import type { SetProperty } from "../useNodeTypeDetailsContentLogic";

export function NodeDetailsFallback(props: {
    node: NodeType;
    setProperty: SetProperty;
    isEditMode?: boolean;
    showValidation?: boolean;
    errors: NodeValidationError[];
}): React.JSX.Element {
    return (
        <>
            <NameField {...props} errors={props.errors} />
            {/* eslint-disable-next-line i18next/no-literal-string */}
            <span>Node type not known.</span>
        </>
    );
}
