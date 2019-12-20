import React from "react"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"
import PropTypes from "prop-types"

export default function BoolEditor(props) {

  const {
    renderFieldLabel, fieldLabel, fieldName, expressionObj, isMarked, readOnly, onValueChange, switchable, toggleEditor,
    shouldShowSwitch, rowClassName, valueClassName, displayRawEditor
  } = props

  const trueValue = {expression: "true", label: "true"}
  const falseValue = {expression: "false", label: "false"}

  return (
    <ExpressionWithFixedValues
      values={[
        trueValue,
        falseValue
      ]}
      defaultValue={trueValue}
      renderFieldLabel={renderFieldLabel}
      fieldLabel={fieldLabel}
      expressionObj={expressionObj}
      onValueChange={onValueChange}
      readOnly={readOnly}
      switchable={switchable}
      toggleEditor={toggleEditor}
      shouldShowSwitch={shouldShowSwitch}
      rowClassName={rowClassName}
      valueClassName={valueClassName}
      displayRawEditor={displayRawEditor}
    />
  )
}

BoolEditor.propTypes = {
  renderFieldLabel: PropTypes.func,
  fieldLabel: PropTypes.string,
  expressionObj: PropTypes.object,
  onValueChange: PropTypes.func,
  readOnly: PropTypes.bool,
  switchable: PropTypes.bool,
  toggleEditor: PropTypes.func,
  shouldShowSwitch: PropTypes.bool,
  rowClassName: PropTypes.string,
  valueClassName: PropTypes.string
}