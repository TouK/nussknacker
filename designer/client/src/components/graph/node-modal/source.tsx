import React from "react";

import type ProcessUtils from "../../../common/ProcessUtils";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { SourceSinkCommon } from "./SourceSinkCommon";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface SourceProps {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    parameterDefinitions: UIParameter[];
    showSwitch?: boolean;
    node: NodeType;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showValidation?: boolean;
    isEditMode?: boolean;
}

export function Source({
    renderFieldLabel,
    setProperty,
    showSwitch,
    errors,
    findAvailableVariables,
    node,
    parameterDefinitions,
    isEditMode,
    showValidation,
}: SourceProps): JSX.Element {
    return (
        <SourceSinkCommon
            isEditMode={isEditMode}
            showValidation={showValidation}
            showSwitch={showSwitch}
            node={node}
            findAvailableVariables={findAvailableVariables}
            parameterDefinitions={parameterDefinitions}
            errors={errors}
            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
        />
    );
}
