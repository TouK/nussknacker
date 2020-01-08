import React from "react"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"
import PropTypes from "prop-types"

export default function BoolEditor(props) {

  const {
    renderFieldLabel, fieldLabel, fieldName, expressionObj, isMarked, readOnly, onValueChange, switchableTo, toggleEditor,
    shouldShowSwitch, rowClassName, valueClassName, displayRawEditor, switchableToHint, notSwitchableToHint
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
      switchableTo={switchableTo}
      switchableToHint={switchableToHint}
      notSwitchableToHint={notSwitchableToHint}
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

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return (expression === "true" || expression === "false") && language === supportedLanguage
}

const supportedLanguage = "spel"
const supportedFieldType = "Boolean"

BoolEditor.switchableTo = (expressionObj) => parseable(expressionObj) || _.isEmpty(expressionObj.expression)

BoolEditor.switchableToHint = "Switch to basic mode"

BoolEditor.notSwitchableToHint = "Expression must be equal to true or false to switch to basic mode"

BoolEditor.isSupported = (fieldType) => fieldType === supportedFieldType
