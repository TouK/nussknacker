import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {DatepickerEditor, DatepickerEditorProps} from "./DatepickerEditor"
import {isEmpty} from "lodash"
import {FormatterType, spelFormatters, typeFormatters} from "../Formatter"
import moment from "moment"

const dateFormat = "YYYY-MM-DD"
const timeFormat = "HH:mm"
const dateTimeFormat = `${dateFormat} ${timeFormat}`
const isParseable = (expression: ExpressionObj): boolean => {
  const date = spelFormatters[FormatterType.DateTime].decode(expression.expression)
  return date && moment(date, dateTimeFormat).isValid()
}

export function DateTimeEditor(props: Omit<DatepickerEditorProps, "dateFormat" | "expressionType">) {

  const {formatter} = props
  const dateFormatter = formatter == null ? typeFormatters[FormatterType.DateTime] : formatter

  return (
    <DatepickerEditor
      {...props}
      momentFormat={dateTimeFormat}
      dateFormat={dateFormat}
      timeFormat={timeFormat}
      formatter={dateFormatter}
    />
  )
}

DateTimeEditor.switchableToHint = () => i18next.t("editors.LocalDateTime.switchableToHint", "Switch to basic mode")
DateTimeEditor.notSwitchableToHint = () => i18next.t(
  "editors.LocalDateTime.notSwitchableToHint",
  "Expression must be valid dateTime to switch to basic mode"
)
DateTimeEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression)

