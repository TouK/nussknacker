import { useTestResults } from "./TestResultsWrapper";
import ExpressionField from "./editors/expression/ExpressionField";
import React, { ComponentType, PropsWithChildren, useMemo } from "react";
import { NodeId, NodeType, NodeValidationError, Parameter, UIParameter, VariableTypes } from "../../../types";
import { getValidationErrorsForField } from "./editors/Validators";
import { findParamDefinitionByName } from "./parameterHelpers";

export type FieldWrapperProps = PropsWithChildren<Omit<ParameterExpressionFieldProps, "FieldWrapper">>;

export type ParameterExpressionFieldProps = {
    listFieldPath: string;
    parameter: Parameter;

    FieldWrapper?: ComponentType<FieldWrapperProps>;
    errors: NodeValidationError[];
    findAvailableVariables?: (nodeId: NodeId, parameterDefinition?: UIParameter) => VariableTypes;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
};

//this is for "dynamic" parameters in sources, sinks, services etc.
export function ParameterExpressionField({ FieldWrapper = React.Fragment, ...props }: ParameterExpressionFieldProps): JSX.Element {
    const {
        errors,
        findAvailableVariables,
        isEditMode,
        listFieldPath,
        node,
        parameter,
        parameterDefinitions,
        renderFieldLabel,
        setProperty,
        showSwitch,
        showValidation,
    } = props;

    const expressionProperty = "expression";
    const testResultsState = useTestResults();
    const variableTypes = useMemo(
        () =>
            findAvailableVariables(
                node.id,
                parameterDefinitions?.find((p) => p.name === parameter.name),
            ),
        [findAvailableVariables, node.id, parameter.name, parameterDefinitions],
    );

    return (
        <FieldWrapper {...props}>
            <ExpressionField
                fieldName={parameter.name}
                fieldLabel={parameter.name}
                exprPath={`${listFieldPath}.${expressionProperty}`}
                isEditMode={isEditMode}
                editedNode={node}
                showValidation={showValidation}
                showSwitch={showSwitch}
                parameterDefinition={findParamDefinitionByName(parameterDefinitions, parameter.name)}
                setNodeDataAt={setProperty}
                testResultsToShow={testResultsState.testResultsToShow}
                renderFieldLabel={renderFieldLabel}
                variableTypes={variableTypes}
                fieldErrors={getValidationErrorsForField(errors, parameter.name)}
            />
        </FieldWrapper>
    );
}
