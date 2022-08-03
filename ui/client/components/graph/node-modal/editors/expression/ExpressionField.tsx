import _ from "lodash"
import React, {useCallback} from "react"
import {NodeType, UIParameter, VariableTypes} from "../../../../../types"
import {UnknownFunction} from "../../../../../types/common"
import ExpressionTestResults from "../../tests/ExpressionTestResults"
import EditableEditor from "../EditableEditor"
import {Error} from "../Validators"
import {EditorType} from "./Editor"
import {NodeResultsForContext} from "../../../../../common/TestResultUtils"

type Props = {
  fieldName: string,
  fieldLabel: string,
  exprPath: string,
  isEditMode: boolean,
  editedNode: NodeType,
  isMarked: (...args: unknown[]) => boolean,
  showValidation: boolean,
  showSwitch: boolean,
  parameterDefinition: UIParameter,
  setNodeDataAt: <T extends any>(propToMutate: string, newValue: T, defaultValue?: T) => void,
  testResultsToShow: NodeResultsForContext,
  renderFieldLabel: UnknownFunction,
  errors: Array<Error>,
  variableTypes: VariableTypes,
}

function ExpressionField(props: Props): JSX.Element {
  const {
    fieldName, fieldLabel, exprPath, isEditMode, editedNode, isMarked, showValidation, showSwitch,
    parameterDefinition, setNodeDataAt, testResultsToShow, renderFieldLabel,
    errors, variableTypes,
  } = props

  const readOnly = !isEditMode
  const exprTextPath = `${exprPath}.expression`
  const expressionObj = _.get(editedNode, exprPath)
  const marked = isMarked(exprTextPath)
  const editor = parameterDefinition?.editor || {}

  const onValueChange = useCallback((newValue) => setNodeDataAt(exprTextPath, newValue), [exprTextPath, setNodeDataAt])

  if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR) {
    return (
      <EditableEditor
        fieldLabel={fieldLabel}
        fieldName={fieldName}
        param={parameterDefinition}
        expressionObj={expressionObj}
        renderFieldLabel={renderFieldLabel}
        isMarked={marked}
        showSwitch={showSwitch}
        readOnly={readOnly}
        onValueChange={onValueChange}
        errors={errors}
        variableTypes={variableTypes}
        showValidation={showValidation}
      />
    )
  }

  return (
    <ExpressionTestResults
      fieldName={fieldName}
      resultsToShow={testResultsToShow}
    >
      <EditableEditor
        param={parameterDefinition}
        renderFieldLabel={renderFieldLabel}
        fieldLabel={fieldLabel}
        fieldName={fieldName}
        expressionObj={expressionObj}
        isMarked={marked}
        showValidation={showValidation}
        showSwitch={showSwitch}
        readOnly={readOnly}
        variableTypes={variableTypes}
        onValueChange={onValueChange}
        errors={errors}
      />
    </ExpressionTestResults>
  )
}

export default ExpressionField
