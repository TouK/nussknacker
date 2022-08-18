import {useTestResults} from "./TestResultsWrapper"
import ExpressionField from "./editors/expression/ExpressionField"
import {findParamDefinitionByName} from "./FieldLabel"
import React from "react"
import {StaticExpressionFieldProps} from "./NodeDetailsContentProps3"

//this is for "static" fields like expressions in filters, switches etc.
export function StaticExpressionField({
  fieldLabel,
  isMarked,
  renderFieldLabel,
  setProperty,
  parameterDefinitions,
  showSwitch,
  findAvailableVariables,
  showValidation,
  fieldErrors,
  originalNodeId,
  isEditMode,
  editedNode,
}: StaticExpressionFieldProps): JSX.Element {
  const fieldName = "expression"
  const expressionProperty = "expression"
  const testResultsState = useTestResults()

  return (
    <ExpressionField
      fieldName={fieldName}
      fieldLabel={fieldLabel}
      exprPath={`${expressionProperty}`}
      isEditMode={isEditMode}
      editedNode={editedNode}
      isMarked={isMarked}
      showValidation={showValidation}
      showSwitch={showSwitch}
      parameterDefinition={findParamDefinitionByName(parameterDefinitions, fieldName)}
      setNodeDataAt={setProperty}
      testResultsToShow={testResultsState.testResultsToShow}
      renderFieldLabel={renderFieldLabel}
      variableTypes={findAvailableVariables(originalNodeId, undefined)}
      errors={fieldErrors || []}
    />
  )
}
