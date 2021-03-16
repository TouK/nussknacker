import React from "react"
import {UnknownFunction} from "../../../../../types/common"
import Input from "../field/Input"
import {SimpleEditor} from "./Editor"
import {Formatter, FormatterType, typeFormatters} from "./Formatter"
import i18next from "i18next"
import {ExpressionLang} from "./types"

type Props = {
  expressionObj: $TodoType,
  onValueChange: UnknownFunction,
  className: string,
  formatter: Formatter,
}

const StringEditor: SimpleEditor<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className, formatter} = props
  const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter

  return (
    <Input
      {...props}
      onChange={(event) => onValueChange(stringFormatter.encode(event.target.value))}
      value={stringFormatter.decode(expressionObj.expression)}
      formattedValue={expressionObj.expression}
      className={className}
    />
  )
}

//TODO handle expressions with escaped '/"
const stringPattern = /(^'.*'$)|(^".*"$)/

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return stringPattern.test(expression) && language === ExpressionLang.SpEL
}

StringEditor.switchableTo = (expressionObj) => parseable(expressionObj)
StringEditor.switchableToHint = () => i18next.t("editors.string.switchableToHint", "Switch to basic mode")
StringEditor.notSwitchableToHint = () => i18next.t("editors.string.notSwitchableToHint", "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode")

export default StringEditor
