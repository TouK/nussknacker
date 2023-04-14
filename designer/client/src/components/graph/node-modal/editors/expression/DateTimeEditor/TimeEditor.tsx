import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {isEmpty} from "lodash"
import {DatepickerEditor, DatepickerEditorProps} from "./DatepickerEditor"
import {FormatterType, spelFormatters, typeFormatters} from "../Formatter"
import moment from "moment"

const timeFormat = "HH:mm:ss"
const isParseable = (expression: ExpressionObj): boolean => {
  const date = spelFormatters[FormatterType.Time].decode(expression.expression)
  return date && moment(date, timeFormat).isValid()
}

export function TimeEditor(props: Omit<DatepickerEditorProps, "dateFormat" | "expressionType">) {

  const {formatter} = props
  const dateFormatter = formatter == null ? typeFormatters[FormatterType.Time] : formatter

  return (
    <DatepickerEditor
      {...props}
      momentFormat={timeFormat}
      dateFormat={null}
      timeFormat={timeFormat}
      formatter={dateFormatter}
    />
  )
}

TimeEditor.switchableToHint = () => i18next.t("editors.LocalTime.switchableToHint", "Switch to basic mode")
TimeEditor.notSwitchableToHint = () => i18next.t("editors.LocalTime.notSwitchableToHint", "Expression must be valid time to switch to basic mode")
TimeEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression)

