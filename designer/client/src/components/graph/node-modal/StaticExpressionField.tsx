import React from "react";

import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../types/validation";
import ExpressionField from "./editors/expression/ExpressionField";
import { getValidationErrorsForField } from "./editors/Validators";
import { findParamDefinitionByName } from "./parameterHelpers";
import { useTestResults } from "./TestResultsWrapper";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

//this is for "static" fields like expressions in filters, switches etc.
export function StaticExpressionField({
    errors,
    fieldLabel,
    variableTypes,
    isEditMode,
    node,
    parameterDefinitions,

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

    setProperty: SetProperty;
    showSwitch?: boolean;
    showValidation?: boolean;
}): React.JSX.Element {
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
            variableTypes={variableTypes}
            fieldErrors={getValidationErrorsForField(errors, `$${fieldName}`)}
        />
    );
}
