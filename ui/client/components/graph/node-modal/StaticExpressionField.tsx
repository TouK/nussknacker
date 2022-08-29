import {useTestResults} from "./TestResultsWrapper"
import ExpressionField from "./editors/expression/ExpressionField"
import {findParamDefinitionByName} from "./FieldLabel"
import React from "react"
import {NodeType, NodeValidationError, UIParameter} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"

//this is for "static" fields like expressions in filters, switches etc.
export function StaticExpressionField({
  fieldErrors,
  fieldLabel,
  findAvailableVariables,
  isEditMode,
  node,
  parameterDefinitions,
  renderFieldLabel,
  setProperty,
  showSwitch,
  showValidation,
}: {
  fieldErrors?: NodeValidationError[],
  fieldLabel: string,
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  isEditMode?: boolean,
  node: NodeType,
  parameterDefinitions: UIParameter[],
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showSwitch?: boolean,
  showValidation?: boolean,
}): JSX.Element {
  const fieldName = "expression"
  const expressionProperty = "expression"
  const testResultsState = useTestResults()
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
      variableTypes={findAvailableVariables(node.id, undefined)}
      errors={fieldErrors || []}
    />
  )
}
