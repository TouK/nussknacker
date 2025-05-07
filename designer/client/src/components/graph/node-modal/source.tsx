import React from "react";

import type ProcessUtils from "../../../common/ProcessUtils";
import type { NodeType, NodeValidationError, UIParameter } from "../../../types";
import type { SetProperty } from "./NodeTypeDetailsContent";
import { SourceSinkCommon } from "./SourceSinkCommon";

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
