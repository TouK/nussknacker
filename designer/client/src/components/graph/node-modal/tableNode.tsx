import { NodeType, NodeValidationError, ProcessDefinitionData, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import React, { useMemo } from "react";
import { NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { IdField } from "./IdField";
import { NodeField } from "./NodeField";
import { FieldType } from "./editors/field/Field";
import { errorValidator } from "./editors/Validators";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { DescriptionField } from "./DescriptionField";

export const NodeContext = React.createContext<{
    node: NodeType;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
}>(null);

export function TableNode({
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
    const hasOutputVar = useMemo(
        (): boolean =>
            !!ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType || !!node.outputVar,
        [node, processDefinitionData.processDefinition],
    );
    const tableData = node.parameters.findIndex((p) => p.name === "tableData");
    return (
        <NodeTableBody>
            <IdField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                errors={fieldErrors}
            />
            {hasOutputVar && (
                <NodeField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    fieldType={FieldType.input}
                    fieldLabel={"Output variable name"}
                    fieldProperty={"outputVar"}
                    validators={[errorValidator(fieldErrors || [], "outputVar")]}
                />
            )}
            {node.parameters?.map((param, index) => {
                return (
                    <div className="node-block" key={node.id + param.name + index}>
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
                            listFieldPath={`parameters[${index}]`}
                        />
                    </div>
                );
            })}
            <div className="node-block">
                <NodeContext.Provider value={{ node, setProperty }}>
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
                        parameter={node.parameters[tableData]}
                        listFieldPath={`parameters[${tableData}]`}
                    />
                </NodeContext.Provider>
            </div>
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
