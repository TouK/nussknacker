import { NodeId, NodeType, NodeValidationError, Parameter, UIParameter, VariableTypes } from "../../../types";
import { ParameterExpressionField } from "./ParameterExpressionField";
import React from "react";

interface ParametersListProps {
    parameters: Parameter[];
    node: NodeType;
    errors: NodeValidationError[];
    findAvailableVariables: (nodeId: NodeId, parameterDefinition?: UIParameter) => VariableTypes;
    isEditMode?: boolean;
    getListFieldPath: (index: number) => string;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}

export const ParametersList = ({
    parameters = [],
    node,
    errors,
    findAvailableVariables,
    isEditMode,
    getListFieldPath,
    parameterDefinitions,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: ParametersListProps) => (
    <>
        {parameters.map((param, index) => (
            <div className="node-block" key={node.id + param.name + index}>
                <ParameterExpressionField
                    errors={errors}
                    findAvailableVariables={findAvailableVariables}
                    isEditMode={isEditMode}
                    listFieldPath={getListFieldPath(index)}
                    node={node}
                    parameter={param}
                    parameterDefinitions={parameterDefinitions}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    showSwitch={showSwitch}
                    showValidation={showValidation}
                />
            </div>
        ))}
    </>
);
