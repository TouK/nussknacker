import type { ComponentType, PropsWithChildren, ReactNode } from "react";
import React, { useMemo } from "react";

import type { UIParameter } from "../../../types/definition";
import type { NodeId, NodeType, Parameter } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import ExpressionField from "./editors/expression/ExpressionField";
import type { FieldError } from "./editors/Validators";
import { getValidationErrorsForField } from "./editors/Validators";
import { findParamDefinitionByName } from "./parameterHelpers";
import { useTestResults } from "./TestResultsWrapper";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

export type FieldWrapperProps = PropsWithChildren<
    Omit<ParameterExpressionFieldProps, "FieldWrapper"> & {
        fieldErrors: FieldError[];
    }
>;

export type ParameterExpressionFieldProps = {
    listFieldPath: string;
    parameter: Parameter;

    FieldWrapper?: ComponentType<FieldWrapperProps>;
    errors: NodeValidationError[];
    findAvailableVariables?: (nodeId: NodeId, parameterDefinition?: UIParameter) => VariableTypes;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];

    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
    endAdornment?: ReactNode;
    inputAdornmentEnd?: ReactNode;
};

//this is for "dynamic" parameters in sources, sinks, services etc.
export function ParameterExpressionField({ FieldWrapper, ...props }: ParameterExpressionFieldProps): React.JSX.Element {
    const {
        errors,
        findAvailableVariables,
        isEditMode,
        listFieldPath,
        node,
        parameter,
        parameterDefinitions,

        setProperty,
        showSwitch,
        showValidation,
        endAdornment,
        inputAdornmentEnd,
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

    const fieldErrors = getValidationErrorsForField(errors, parameter.name);

    const parameterDefinition = useMemo(
        () => findParamDefinitionByName(parameterDefinitions, parameter.name),
        [parameter.name, parameterDefinitions],
    );

    const field = useMemo(() => {
        return (
            <ExpressionField
                fieldName={parameter.name}
                fieldLabel={parameter.name}
                exprPath={`${listFieldPath}.${expressionProperty}`}
                isEditMode={isEditMode}
                editedNode={node}
                showValidation={showValidation}
                showSwitch={showSwitch}
                parameterDefinition={parameterDefinition}
                setNodeDataAt={setProperty}
                testResultsToShow={testResultsState.testResultsToShow}
                variableTypes={variableTypes}
                fieldErrors={fieldErrors}
                endAdornment={endAdornment}
                inputAdornmentEnd={inputAdornmentEnd}
            />
        );
    }, [
        endAdornment,
        inputAdornmentEnd,
        fieldErrors,
        isEditMode,
        listFieldPath,
        node,
        parameter.name,
        parameterDefinition,
        setProperty,
        showSwitch,
        showValidation,
        testResultsState.testResultsToShow,
        variableTypes,
    ]);

    return FieldWrapper ? (
        <FieldWrapper {...props} fieldErrors={fieldErrors}>
            {field}
        </FieldWrapper>
    ) : (
        <>{field}</>
    );
}
