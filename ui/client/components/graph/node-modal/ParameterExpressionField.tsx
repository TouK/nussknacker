import {useTestResults} from "./TestResultsWrapper"
import ExpressionField from "./editors/expression/ExpressionField"
import {findParamDefinitionByName} from "./FieldLabel"
import React from "react"
import {ParameterExpressionFieldProps} from "./NodeDetailsContentProps3"

//this is for "dynamic" parameters in sources, sinks, services etc.
export function ParameterExpressionField({
  parameter,
  listFieldPath,
  isMarked, renderFieldLabel, setProperty,
  ...props
}: ParameterExpressionFieldProps): JSX.Element {
  const expressionProperty = "expression"
  const testResultsState = useTestResults()

  return (
    <ExpressionField
      fieldName={parameter.name}
      fieldLabel={parameter.name}
      exprPath={`${listFieldPath}.${expressionProperty}`}
      isEditMode={props.isEditMode}
      editedNode={props.editedNode}
      isMarked={isMarked}
      showValidation={props.showValidation}
      showSwitch={props.showSwitch}
      parameterDefinition={findParamDefinitionByName(props.parameterDefinitions, parameter.name)}
      setNodeDataAt={setProperty}
      testResultsToShow={testResultsState.testResultsToShow}
      renderFieldLabel={renderFieldLabel}
      variableTypes={props.findAvailableVariables(props.originalNodeId, props.parameterDefinitions?.find(p => p.name === parameter.name))}
      errors={props.fieldErrors || []}
    />
  )
}
