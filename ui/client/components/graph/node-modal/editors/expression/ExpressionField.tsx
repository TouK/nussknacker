import _ from "lodash"
import React from "react"
import ExpressionTestResults from "../../tests/ExpressionTestResults"
import EditableEditor from "../EditableEditor"
import {EditorType} from "./Editor"

type Props = {
  fieldName: string,
  fieldLabel: string,
  exprPath: string,
  isEditMode: boolean,
  editedNode: $TodoType,
  isMarked: Function,
  showValidation: boolean,
  showSwitch: boolean,
  nodeObjectDetails: $TodoType,
  setNodeDataAt: Function,
  testResultsToShow: $TodoType,
  testResultsToHide: $TodoType,
  toggleTestResult: Function,
  renderFieldLabel: Function,
  fieldType: string,
  errors: Array<Error>,
}

class ExpressionField extends React.Component<Props> {

  render() {
    const {
      fieldName, fieldLabel, exprPath, isEditMode, editedNode, isMarked, showValidation, showSwitch,
      nodeObjectDetails, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult, renderFieldLabel, fieldType,
      errors,
    } = this.props
    const readOnly = !isEditMode
    const exprTextPath = `${exprPath}.expression`
    const expressionObj = _.get(editedNode, exprPath)
    const marked = isMarked(exprTextPath)
    const editor = this.findParamByName(fieldName)?.editor || {}

    if (editor.type === EditorType.FIXED_VALUES_PARAMETER_EDITOR)
      return (
        <EditableEditor
          fieldType={EditorType.FIXED_VALUES_PARAMETER_EDITOR}
          fieldLabel={fieldLabel}
          fieldName={fieldName}
          param={this.findParamByName(fieldLabel)}
          expressionObj={expressionObj}
          renderFieldLabel={renderFieldLabel}
          values={editor.possibleValues}
          isMarked={marked}
          showSwitch={showSwitch}
          readOnly={readOnly}
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
          errors={errors}
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
          param={this.findParamByName(fieldLabel)}
          editorName={EditorType.RAW_PARAMETER_EDITOR}
          renderFieldLabel={renderFieldLabel}
          fieldLabel={fieldLabel}
          fieldName={fieldName}
          expressionObj={expressionObj}
          isMarked={marked}
          showValidation={showValidation}
          showSwitch={showSwitch}
          readOnly={readOnly}
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
          errors={errors}
        />
      </ExpressionTestResults>
    )
  }

  findParamByName = (paramName) => _.get(this.props, "nodeObjectDetails.parameters", [])
    .find((param) => param.name === paramName)
}

export default ExpressionField
