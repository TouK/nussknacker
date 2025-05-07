import type { PropsWithChildren } from "react";
import React, { useMemo } from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import type { NodeType, NodeValidationError, ProcessDefinitionData, UIParameter } from "../../../types";
import { AggregateParametersList } from "./aggregateParametersList";
import { DescriptionField } from "./DescriptionField";
import { FieldType } from "./editors/field/Field";
import { IdField } from "./IdField";
import { isAggregate } from "./isAggregate";
import { NodeField } from "./NodeField";
import type { SetProperty } from "./NodeTypeDetailsContent";
import { ParametersListAdvanced } from "./parametersListAdvanced";

export type CustomNodeProps = {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    processDefinitionData: ProcessDefinitionData;
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
};

export function CustomNode({
    children,
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
}: PropsWithChildren<CustomNodeProps>): JSX.Element {
    const hasOutputVar = useMemo(
        (): boolean => !!ProcessUtils.extractComponentDefinition(node, processDefinitionData.components)?.returnType || !!node.outputVar,
        [node, processDefinitionData.components],
    );

    const ParametersComponent = useMemo(() => {
        return isAggregate(node) ? AggregateParametersList : ParametersListAdvanced;
    }, [node]);

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
            {hasOutputVar && (
                <NodeField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    fieldType={FieldType.input}
                    fieldLabel={"Output variable name"}
                    fieldName={"outputVar"}
                    errors={errors}
                />
            )}
            {children}
            <ParametersComponent
                parameters={node.parameters}
                showSwitch={showSwitch}
                findAvailableVariables={findAvailableVariables}
                parameterDefinitions={parameterDefinitions}
                errors={errors}
                node={node}
                isEditMode={isEditMode}
                showValidation={showValidation}
                renderFieldLabel={renderFieldLabel}
                setProperty={setProperty}
                getListFieldPath={(index: number) => `parameters[${index}]`}
            >
                <DescriptionField
                    node={node}
                    isEditMode={isEditMode}
                    showValidation={showValidation}
                    renderFieldLabel={renderFieldLabel}
                    setProperty={setProperty}
                    errors={errors}
                />
            </ParametersComponent>
        </>
    );
}
