import React from "react";

import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import { DisableField } from "./DisableField";
import { NamedParamsDataMapper } from "./NamedParamsDataMapper";
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
    const isKafkaDynamicMode =
        isEditMode &&
        (node as unknown as { ref?: { typ?: string } }).ref?.typ?.endsWith("kafka") &&
        !parameterDefinitions.find((p) => p.name === "Value");

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
            {isKafkaDynamicMode && (
                <NamedParamsDataMapper
                    node={node}
                    parameterDefinitions={parameterDefinitions}
                    setProperty={setProperty}
                    parametersBasePath="ref.parameters"
                />
            )}
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
