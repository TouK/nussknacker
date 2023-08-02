import { NodeType, NodeValidationError, ProcessDefinitionData, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import { useTestResults } from "./TestResultsWrapper";
import React, { useMemo } from "react";
import { NodeTableBody } from "./NodeDetailsContent/NodeTable";
import { IdField } from "./IdField";
import { NodeField } from "./NodeField";
import { FieldType } from "./editors/field/Field";
import { errorValidator } from "./editors/Validators";
import NodeUtils from "../NodeUtils";
import BranchParameters from "./BranchParameters";
import { ParameterExpressionField } from "./ParameterExpressionField";
import { DescriptionField } from "./DescriptionField";
import { Join } from "../../../newTypes/processDefinitionData";
import { NodeData } from "../../../newTypes/displayableProcess";

export function JoinCustomNode({
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
    node: NodeData & Join;
    parameterDefinitions: UIParameter[];
    processDefinitionData?: ProcessDefinitionData;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeData>(property: K, newValue: NodeData[K], defaultValue?: NodeData[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}): JSX.Element {
    const testResultsState = useTestResults();
    const hasOutputVar = useMemo(
        (): boolean =>
            !!ProcessUtils.findNodeObjectTypeDefinition(node, processDefinitionData.processDefinition)?.returnType || !!node.outputVar,
        [node, processDefinitionData.processDefinition],
    );
    return (
        <NodeTableBody>
            <IdField
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
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
            {node.type === "Join" && (
                <BranchParameters
                    node={node}
                    showValidation={showValidation}
                    showSwitch={showSwitch}
                    isEditMode={isEditMode}
                    errors={fieldErrors || []}
                    parameterDefinitions={parameterDefinitions}
                    setNodeDataAt={setProperty}
                    testResultsToShow={testResultsState.testResultsToShow}
                    findAvailableVariables={findAvailableVariables}
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
