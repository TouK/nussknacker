import React from "react";

import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DisableField } from "./DisableField";
import { SourceSinkCommon } from "./SourceSinkCommon";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface SinkProps {
    errors: NodeValidationError[];
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}

export function Sink({
    errors,
    isEditMode,
    node,
    parameterDefinitions,
    setProperty,
    showSwitch,
    showValidation,
}: SinkProps): React.JSX.Element {
    return (
        <SourceSinkCommon
            isEditMode={isEditMode}
            showValidation={showValidation}
            showSwitch={showSwitch}
            node={node}
            parameterDefinitions={parameterDefinitions}
            errors={errors}
            setProperty={setProperty}
        >
            <div>
                <DisableField
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    node={node}
                    setProperty={setProperty}
                    errors={errors}
                />
            </div>
        </SourceSinkCommon>
    );
}
