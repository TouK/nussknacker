import type { ComponentType, PropsWithChildren, ReactNode } from "react";
import React, { useMemo } from "react";

import type { NodeId, NodeType, NodeValidationError, Parameter, UIParameter, VariableTypes } from "../../../types";
import ExpressionField from "./editors/expression/ExpressionField";
import { getValidationErrorsForField } from "./editors/Validators";
import { findParamDefinitionByName } from "./parameterHelpers";
import { useTestResults } from "./TestResultsWrapper";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

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
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
    endAdornment?: ReactNode;
};

//this is for "dynamic" parameters in sources, sinks, services etc.
export function ParameterExpressionField({ FieldWrapper, ...props }: ParameterExpressionFieldProps): JSX.Element {
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
        endAdornment,
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

    const field = useMemo(
        () => (
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
                endAdornment={endAdornment}
            />
        ),
        [
            endAdornment,
            errors,
            isEditMode,
            listFieldPath,
            node,
            parameter.name,
            parameterDefinitions,
            renderFieldLabel,
            setProperty,
            showSwitch,
            showValidation,
            testResultsState.testResultsToShow,
            variableTypes,
        ],
    );

    return FieldWrapper ? <FieldWrapper {...props}>{field}</FieldWrapper> : <>{field}</>;
}
