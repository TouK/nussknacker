import {UnknownFunction} from "../../../../../types/common"
import {Formatter, FormatterType, typeFormatters} from "./Formatter"
import {Editor, SimpleEditor} from "./Editor"
import i18next from "i18next"
import React from "react"
import Textarea from "../field/Textarea"

type Props = {
    expressionObj: $TodoType,
    onValueChange: UnknownFunction,
    className: string,
    formatter: Formatter,
}

const TextareaEditor: SimpleEditor<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className, formatter} = props
  const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter

  return (
    <Textarea
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
  return stringPattern.test(expression) && language === "spel"
}

TextareaEditor.switchableTo = (expressionObj) => parseable(expressionObj)
TextareaEditor.switchableToHint = () => i18next.t("editors.textarea.switchableToHint", "Switch to basic mode")
TextareaEditor.notSwitchableToHint = () => i18next.t("editors.textarea.notSwitchableToHint", "Expression must be a simple string literal i.e. text surrounded by single or double quotation marks to switch to basic mode")

export default TextareaEditor
