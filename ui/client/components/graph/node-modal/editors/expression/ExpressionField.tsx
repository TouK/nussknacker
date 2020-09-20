import _ from "lodash"
import React from "react"
import {NodeType, UIParameter, VariableTypes} from "../../../../../types"
import {UnknownFunction} from "../../../../../types/common"
import ExpressionTestResults from "../../tests/ExpressionTestResults"
import EditableEditor from "../EditableEditor"
import {Error} from "../Validators"
import {EditorType} from "./Editor"

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
  setNodeDataAt: UnknownFunction,
  testResultsToShow: $TodoType,
  testResultsToHide: $TodoType,
  toggleTestResult: UnknownFunction,
  renderFieldLabel: UnknownFunction,
  errors: Array<Error>,
  variableTypes: VariableTypes,
}

class ExpressionField extends React.Component<Props> {

  render() {
    const {
      fieldName, fieldLabel, exprPath, isEditMode, editedNode, isMarked, showValidation, showSwitch,
      parameterDefinition, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult, renderFieldLabel,
      errors, variableTypes,
    } = this.props

    const readOnly = !isEditMode
    const exprTextPath = `${exprPath}.expression`
    const expressionObj = _.get(editedNode, exprPath)
    const marked = isMarked(exprTextPath)
    const editor = parameterDefinition?.editor || {}

    if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR)
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
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
          errors={errors}
          variableTypes={variableTypes}
          showValidation={showValidation}
        />
      )

    return (
      <ExpressionTestResults
        fieldName={fieldName}
        resultsToShow={testResultsToShow}
        resultsToHide={testResultsToHide}
        toggleResult={toggleTestResult}
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
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
          errors={errors}
        />
      </ExpressionTestResults>
    )
  }
}

export default ExpressionField
