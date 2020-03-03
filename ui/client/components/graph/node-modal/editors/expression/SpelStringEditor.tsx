import StringEditor from "./StringEditor"
import React from "react"
import {ExpressionObj} from "./types"
import _ from "lodash"
import {Editor} from "./Editor"

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: Function,
  className: string,
}

const SpelStringEditor: Editor<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className} = props

  const defaultQuotationMark = "'"
  const expressionQuotationMark = expressionObj.expression.charAt(0)
  const quotationMark = !_.isEmpty(expressionQuotationMark) ? expressionQuotationMark : defaultQuotationMark

  const trim = (value) => value.substring(1, value.length - 1)
  const format = (value) => quotationMark + value + quotationMark

  return (
    <StringEditor
      expressionObj={expressionObj}
      onValueChange={onValueChange}
      className={className}
      trim={trim}
      format={format}
    />
  )
}

//TODO handle expressions with escaped '/"
const stringPattern = /(^'.*'$)|(^".*"$)/

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return stringPattern.test(expression) && language === "spel"
}

SpelStringEditor.switchableTo = (expressionObj) => parseable(expressionObj)
SpelStringEditor.switchableToHint = () => "Switch to basic mode"
SpelStringEditor.notSwitchableToHint = () => "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode"

export default SpelStringEditor
