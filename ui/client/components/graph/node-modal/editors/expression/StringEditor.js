import PropTypes from "prop-types"
import React from "react"
import Input from "../field/Input"

export default function StringEditor(props) {

  const {expressionObj, onValueChange, className} = props

  const defaultQuotationMark = "'"
  const expressionQuotationMark = expressionObj.expression.charAt(0)
  const quotationMark = !_.isEmpty(expressionQuotationMark) ? expressionQuotationMark : defaultQuotationMark

  const format = (value) => quotationMark + value + quotationMark

  const trim = (value) => value.substring(1, value.length - 1)

  return <Input {...props}
                onChange={(event) => onValueChange(format(event.target.value))}
                value={trim(expressionObj.expression)}
                formattedValue={expressionObj.expression}
                className={className}/>
}

//TODO handle expressions with escaped '/"
const stringPattern = /(^'.*'$)|(^".*"$)/

StringEditor.propTypes = {
  expressionObj: PropTypes.object,
  onValueChange: PropTypes.func,
  className: PropTypes.string,
}

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return stringPattern.test(expression) && language === "spel"
}

const supportedFieldType = "String"

StringEditor.switchableTo = (expressionObj) => parseable(expressionObj)

StringEditor.switchableToHint = "Switch to basic mode"

StringEditor.notSwitchableToHint = "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode"

StringEditor.isSupported = (fieldType) => fieldType === supportedFieldType
