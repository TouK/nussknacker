import { useTestResults } from "./TestResultsWrapper";
import ExpressionField from "./editors/expression/ExpressionField";
import { findParamDefinitionByName } from "./ParamLabel";
import React, { useMemo } from "react";
import { NodeType, NodeValidationError, Parameter, UIParameter } from "../../../types";
import ProcessUtils from "../../../common/ProcessUtils";
import { getValidationErrorsForField } from "./editors/Validators";

interface ParameterExpressionField {
    errors: NodeValidationError[];
    findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>;
    isEditMode?: boolean;
    listFieldPath: string;
    node: NodeType;
    parameter: Parameter;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    showSwitch?: boolean;
    showValidation?: boolean;
}
//this is for "dynamic" parameters in sources, sinks, services etc.
export function ParameterExpressionField(props: ParameterExpressionField): JSX.Element {
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
    );
}
