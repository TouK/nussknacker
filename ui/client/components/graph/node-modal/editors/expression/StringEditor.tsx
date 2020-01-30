import React from "react"
import Input from "../field/Input"
import _ from "lodash"
import {EditorType} from "./EditorType"

type Props = {
  expressionObj: $TodoType,
  onValueChange: Function,
  className: string,
}

const StringEditor: EditorType<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className} = props

  const defaultQuotationMark = "'"
  const expressionQuotationMark = expressionObj.expression.charAt(0)
  const quotationMark = !_.isEmpty(expressionQuotationMark) ? expressionQuotationMark : defaultQuotationMark

  const format = (value) => quotationMark + value + quotationMark

  const trim = (value) => value.substring(1, value.length - 1)

  return (
    <Input
      {...props}
      onChange={(event) => onValueChange(format(event.target.value))}
      value={trim(expressionObj.expression)}
      formattedValue={expressionObj.expression}
      className={className}/>
)
}

//TODO handle expressions with escaped '/"
const stringPattern = /(^'.*'$)|(^".*"$)/

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return stringPattern.test(expression) && language === "spel"
}

StringEditor.switchableTo = (expressionObj) => parseable(expressionObj)
StringEditor.switchableToHint = "Switch to basic mode"
StringEditor.notSwitchableToHint = "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode"

export default StringEditor
