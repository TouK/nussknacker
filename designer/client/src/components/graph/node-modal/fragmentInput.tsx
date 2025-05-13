import React, { useCallback } from "react";

import type ProcessUtils from "../../../common/ProcessUtils";
import type { NodeType, NodeValidationError, ProcessDefinitionData, UIParameter } from "../../../types";
import { DescriptionField } from "./DescriptionField";
import { DisableField } from "./DisableField";
import { IdField } from "./IdField";
import OutputParametersList from "./OutputParametersList";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { useParametersList } from "./useParametersList";

interface FragmentInput {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    processDefinitionData?: ProcessDefinitionData;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}

export function FragmentInput(props: FragmentInput): JSX.Element {
    const {
        errors,
        findAvailableVariables,
        isEditMode,
        node,
        parameterDefinitions,
        processDefinitionData,
        renderFieldLabel,
        setProperty,
        showSwitch,
        showValidation,
    } = props;
    const setNodeState = useCallback((newParams) => setProperty("ref.parameters", newParams), [setProperty]);
    const parameters = useParametersList(node, processDefinitionData, isEditMode, setNodeState);

    return (
        <>
            <IdField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            <DisableField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
            {parameters.map((param, index) => (
                <ParameterExpressionField
                    key={`${param.name}-${index}`}
                    showSwitch={showSwitch}
                    findAvailableVariables={findAvailableVariables}
                    parameterDefinitions={parameterDefinitions}
                    errors={errors}
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    parameter={param}
                    listFieldPath={`ref.parameters[${index}]`}
                />
            ))}
            <OutputParametersList
                editedNode={node}
                errors={errors}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                processDefinitionData={processDefinitionData}
            />
            <DescriptionField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={errors}
            />
        </>
    );
}
