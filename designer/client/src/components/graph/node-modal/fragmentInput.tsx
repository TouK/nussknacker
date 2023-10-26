import { NodeType, NodeValidationError, ProcessDefinitionData, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import React, { useCallback } from "react";
import { NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { IdField } from "./IdField";
import { DisableField } from "./DisableField";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { DescriptionField } from "./DescriptionField";
import OutputParametersList from "./OutputParametersList";
import { useParametersList } from "./useParametersList";

interface FragmentInput {
    fieldErrors?: NodeValidationError[];
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
        fieldErrors,
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
        <NodeTableBody>
            <IdField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
            />
            <DisableField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
            />
            {parameters.map((param, index) => (
                <ParameterExpressionField
                    key={`${param.name}-${index}`}
                    showSwitch={showSwitch}
                    findAvailableVariables={findAvailableVariables}
                    parameterDefinitions={parameterDefinitions}
                    fieldErrors={fieldErrors}
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
                fieldErrors={fieldErrors}
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
            />
        </NodeTableBody>
    );
}
