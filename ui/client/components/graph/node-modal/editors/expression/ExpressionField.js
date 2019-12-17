import React from "react"
import _ from "lodash"
import EditableExpression from "./EditableExpression"
import ProcessUtils from "../../../../../common/ProcessUtils"
import EditableExpressionWithTestResults from "./tests/EditableExpressionWithTestResults"

export default class ExpressionField extends React.Component {

  render() {
    const {
      fieldName, fieldLabel, exprPath, validators, isEditMode, editedNode, isMarked, showValidation, showSwitch,
      nodeObjectDetails, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult, renderFieldLabel
    } = this.props
    const readOnly = !isEditMode
    const exprTextPath = `${exprPath}.expression`
    const expressionObj = _.get(editedNode, exprPath)
    const marked = isMarked(exprTextPath)
    const restriction = this.getRestriction(fieldName)

    if (restriction.hasFixedValues)
      return (
        <EditableExpression
          fieldType={"expressionWithFixedValues"}
          fieldLabel={fieldLabel}
          expressionObj={expressionObj}
          renderFieldLabel={renderFieldLabel}
          values={restriction.values}
          isMarked={marked}
          showSwitch={showSwitch}
          readOnly={readOnly}
          onValueChange={(newValue) => setNodeDataAt(exprTextPath, newValue)}
        />
      )

    const param = this.findParamByName(fieldLabel)

    const editableExpression =
      <EditableExpression
        fieldType={param ? ProcessUtils.humanReadableType(param.typ.refClazzName) : "expression"}
        editorName={"rawEditor"}
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

    return (
      <EditableExpressionWithTestResults
        fieldName={fieldName}
        testResultsToShow={testResultsToShow}
        testResultsToHide={testResultsToHide}
        toggleTestResult={toggleTestResult}
        field={editableExpression}
      />
    )
  }

  getRestriction = (fieldName) => {
    const restriction = (this.findParamByName(fieldName) || {}).restriction
    return {
      hasFixedValues: restriction && restriction.type === "FixedExpressionValues",
      values: restriction && restriction.values
    }
  }

  findParamByName(paramName) {
    const {nodeObjectDetails} = this.props
    return (_.get(nodeObjectDetails, "parameters", [])).find((param) => param.name === paramName)
  }
}