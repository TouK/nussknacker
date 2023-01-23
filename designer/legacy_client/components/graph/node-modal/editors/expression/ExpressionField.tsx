import _ from "lodash"
import React, {useCallback} from "react"
import {NodeType, UIParameter, VariableTypes} from "../../../../../types"
import {UnknownFunction} from "../../../../../types/common"
import ExpressionTestResults from "../../tests/ExpressionTestResults"
import EditableEditor from "../EditableEditor"
import {Error} from "../Validators"
import {EditorType} from "./Editor"
import {NodeResultsForContext} from "../../../../../common/TestResultUtils"
import {useDiffMark} from "../../PathsToMark"

type Props = {
  fieldName: string,
  fieldLabel: string,
  exprPath: string,
  isEditMode: boolean,
  editedNode: NodeType,
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
    fieldName, fieldLabel, exprPath, isEditMode, editedNode, showValidation, showSwitch,
    parameterDefinition, setNodeDataAt, testResultsToShow, renderFieldLabel,
    errors, variableTypes,
  } = props
  const [isMarked] = useDiffMark()
  const readOnly = !isEditMode
  const exprTextPath = `${exprPath}.expression`
  const expressionObj = _.get(editedNode, exprPath)
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
        isMarked={isMarked(exprTextPath)}
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
        isMarked={isMarked(exprTextPath)}
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
