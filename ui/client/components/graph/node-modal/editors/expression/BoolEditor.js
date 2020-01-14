import PropTypes from "prop-types"
import React from "react"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"

export default function BoolEditor(props) {

  const {expressionObj, readOnly, onValueChange, className} = props

  const trueValue = {expression: "true", label: "true"}
  const falseValue = {expression: "false", label: "false"}

  return (
    <ExpressionWithFixedValues
      values={[
        trueValue,
        falseValue,
      ]}
      defaultValue={trueValue}
      expressionObj={expressionObj}
      onValueChange={onValueChange}
      readOnly={readOnly}
      className={className}
    />
  )
}

BoolEditor.propTypes = {
  expressionObj: PropTypes.object,
  onValueChange: PropTypes.func,
  readOnly: PropTypes.bool,
  className: PropTypes.string,
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
