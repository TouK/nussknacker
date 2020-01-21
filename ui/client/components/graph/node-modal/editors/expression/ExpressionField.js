import _ from "lodash"
import React from "react"
import ExpressionTestResults from "../../tests/ExpressionTestResults"
import EditableExpression from "./EditableExpression"
import {SimpleEditorTypes} from "./EditorType"

export default class ExpressionField extends React.Component {

  render() {
    const {
      fieldName, fieldLabel, exprPath, validators, isEditMode, editedNode, isMarked, showValidation, showSwitch,
      nodeObjectDetails, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult, renderFieldLabel, fieldType,
    } = this.props
    const readOnly = !isEditMode
    const exprTextPath = `${exprPath}.expression`
    const expressionObj = _.get(editedNode, exprPath)
    const marked = isMarked(exprTextPath)
    const restriction = this.getRestriction(fieldName)

    if (restriction.hasFixedValues)
      return (
        <EditableExpression
          fieldType={SimpleEditorTypes.FIXED_VALUES_EDITOR}
          fieldLabel={fieldLabel}
          param={this.findParamByName(fieldLabel)}
          expressionObj={expressionObj}
          renderFieldLabel={renderFieldLabel}
          values={restriction.values}
          isMarked={marked}
          showSwitch={showSwitch}
          readOnly={readOnly}
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
        />
      )

    return (
      <ExpressionTestResults
        fieldName={fieldName}
        resultsToShow={testResultsToShow}
        resultsToHide={testResultsToHide}
        toggleResult={toggleTestResult}>
        <EditableExpression
          fieldType={fieldType}
          param={this.findParamByName(fieldLabel)}
          editorName={SimpleEditorTypes.RAW_EDITOR}
          renderFieldLabel={renderFieldLabel}
          fieldLabel={fieldLabel}
          fieldName={fieldName}
          expressionObj={expressionObj}
          validators={validators}
          isMarked={marked}
          showValidation={showValidation}
          showSwitch={showSwitch}
          readOnly={readOnly}
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
        />
      </ExpressionTestResults>
    )
  }

  getRestriction = (fieldName) => {
    const restriction = (this.findParamByName(fieldName) || {}).restriction
    return {
      hasFixedValues: restriction && restriction.type === "FixedExpressionValues",
      values: restriction && restriction.values,
    }
  }

  findParamByName = (paramName) => (_.get(this.props, "nodeObjectDetails.parameters", []))
    .find((param) => param.name === paramName)
}
