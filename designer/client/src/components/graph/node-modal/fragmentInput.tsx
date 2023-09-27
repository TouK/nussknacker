import { NodeType, NodeValidationError, ProcessDefinitionData, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import React, { useCallback } from "react";
import { NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { IdField } from "./IdField";
import { DisableField } from "./DisableField";
import ParameterList from "./ParameterList";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { DescriptionField } from "./DescriptionField";
import OutputParametersList from "./OutputParametersList";

export function FragmentInput({
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
}: {
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
}): JSX.Element {
    const setNodeState = useCallback((newParams) => setProperty("ref.parameters", newParams), [setProperty]);
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
            <ParameterList
                processDefinitionData={processDefinitionData}
                editedNode={node}
                setNodeState={setNodeState}
                isEditMode={isEditMode}
                ListField={({ param, path }) => (
                    <ParameterExpressionField
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
                        listFieldPath={path}
                    />
                )}
            />
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
