import React from "react";

import type ProcessUtils from "../../../common/ProcessUtils";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DisableField } from "./DisableField";
import { SourceSinkCommon } from "./SourceSinkCommon";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface SinkProps {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}

export function Sink({
    errors,
    findAvailableVariables,
    isEditMode,
    node,
    parameterDefinitions,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: SinkProps): JSX.Element {
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
        >
            <div>
                <DisableField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    errors={errors}
                />
            </div>
        </SourceSinkCommon>
    );
}
