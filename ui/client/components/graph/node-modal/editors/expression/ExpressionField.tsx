import _ from "lodash"
import React from "react"
import ExpressionTestResults from "../../tests/ExpressionTestResults"
import EditableEditor from "../EditableEditor"
import {EditorType} from "./Editor"
import {NodeType, UIParameter, VariableTypes} from "../../../../../types"

type Props = {
  fieldName: string,
  fieldLabel: string,
  exprPath: string,
  isEditMode: boolean,
  editedNode: NodeType,
  isMarked: Function,
  showValidation: boolean,
  showSwitch: boolean,
  parameterDefinition: UIParameter,
  setNodeDataAt: Function,
  testResultsToShow: $TodoType,
  testResultsToHide: $TodoType,
  toggleTestResult: Function,
  renderFieldLabel: Function,
  fieldType: string,
  errors: Array<Error>,
  variableTypes: VariableTypes,
}

class ExpressionField extends React.Component<Props> {

  render() {
    const {
      fieldName, fieldLabel, exprPath, isEditMode, editedNode, isMarked, showValidation, showSwitch,
      parameterDefinition, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult, renderFieldLabel, fieldType,
      errors, variableTypes,
    } = this.props
    console.log("name", fieldName, variableTypes)

    const readOnly = !isEditMode
    const exprTextPath = `${exprPath}.expression`
    const expressionObj = _.get(editedNode, exprPath)
    const marked = isMarked(exprTextPath)
    const editor = parameterDefinition?.editor || {}

    if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR)
      return (
        <EditableEditor
          fieldType={EditorType.FIXED_VALUES_PARAMETER_EDITOR}
          fieldLabel={fieldLabel}
          fieldName={fieldName}
          param={parameterDefinition}
          expressionObj={expressionObj}
          renderFieldLabel={renderFieldLabel}
          values={editor.possibleValues}
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
          fieldType={fieldType}
          param={parameterDefinition}
          editorName={EditorType.RAW_PARAMETER_EDITOR}
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
