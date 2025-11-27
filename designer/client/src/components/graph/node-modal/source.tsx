import React from "react";

import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { SourceSinkCommon } from "./SourceSinkCommon";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface SourceProps {
    errors: NodeValidationError[];
    parameterDefinitions: UIParameter[];
    showSwitch?: boolean;
    node: NodeType;
    setProperty: SetProperty;
    showValidation?: boolean;
    isEditMode?: boolean;
}

export function Source({
    setProperty,
    showSwitch,
    errors,
    node,
    parameterDefinitions,
    isEditMode,
    showValidation,
}: SourceProps): React.JSX.Element {
    return (
        <SourceSinkCommon
            isEditMode={isEditMode}
            showValidation={showValidation}
            showSwitch={showSwitch}
            node={node}
            parameterDefinitions={parameterDefinitions}
            errors={errors}
            setProperty={setProperty}
        />
    );
}
