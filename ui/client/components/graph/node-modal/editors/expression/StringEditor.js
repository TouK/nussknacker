import React from "react"
import LabeledInput from "../field/LabeledInput"
import PropTypes from "prop-types"

export default function StringEditor(props) {

  const {fieldLabel, renderFieldLabel, expressionObj, onValueChange} = props

  const defaultQuotationMark = "'"
  const expressionQuotationMark = expressionObj.expression.charAt(0)
  const quotationMark = !_.isEmpty(expressionQuotationMark) ? expressionQuotationMark : defaultQuotationMark

  const format = (value) => quotationMark + value + quotationMark

  const trim = (value) => value.substring(1, value.length - 1)

  return <LabeledInput
    {...props}
    renderFieldLabel={() => props.renderFieldLabel(props.fieldLabel)}
    onChange={(event) => onValueChange(format(event.target.value))}
    value={trim(expressionObj.expression)}
  />

}

export const parseableString = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return stringPattern.test(expression) && language === "spel"
}

const stringPattern = /(^['].*'$)|(^".*"$)/

StringEditor.propTypes = {
  fieldLabel: PropTypes.string,
  renderFieldLabel: PropTypes.func,
  expressionObj: PropTypes.object,
  onValueChange: PropTypes.func
}
