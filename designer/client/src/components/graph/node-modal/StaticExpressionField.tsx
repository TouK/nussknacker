import React from "react";

import type { NodeType, NodeValidationError, UIParameter, VariableTypes } from "../../../types";
import ExpressionField from "./editors/expression/ExpressionField";
import { getValidationErrorsForField } from "./editors/Validators";
import type { SetProperty } from "./NodeTypeDetailsContent";
import { findParamDefinitionByName } from "./parameterHelpers";
import { useTestResults } from "./TestResultsWrapper";

//this is for "static" fields like expressions in filters, switches etc.
export function StaticExpressionField({
    errors,
    fieldLabel,
    variableTypes,
    isEditMode,
    node,
    parameterDefinitions,
    renderFieldLabel,
    setProperty,
    showSwitch,
    showValidation,
}: {
    errors: NodeValidationError[];
    fieldLabel: string;
    variableTypes?: VariableTypes;
    isEditMode?: boolean;
    node: NodeType;
    parameterDefinitions: UIParameter[];
    renderFieldLabel: (paramName: string) => JSX.Element;
    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}): JSX.Element {
    const fieldName = "expression";
    const expressionProperty = "expression";
    const testResultsState = useTestResults();
    return (
        <ExpressionField
            fieldName={fieldName}
            fieldLabel={fieldLabel}
            exprPath={`${expressionProperty}`}
            isEditMode={isEditMode}
            editedNode={node}
            showValidation={showValidation}
            showSwitch={showSwitch}
            parameterDefinition={findParamDefinitionByName(parameterDefinitions, fieldName)}
            setNodeDataAt={setProperty}
            testResultsToShow={testResultsState.testResultsToShow}
            renderFieldLabel={renderFieldLabel}
            variableTypes={variableTypes}
            fieldErrors={getValidationErrorsForField(errors, `$${fieldName}`)}
        />
    );
}
