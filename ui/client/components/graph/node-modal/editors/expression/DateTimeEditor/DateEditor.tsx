import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {isEmpty} from "lodash"
import {DatepickerEditor, DatepickerEditorProps} from "./DatepickerEditor"
import {spelFormatters, SpelFormatterType} from "../Formatter"
import moment from "moment"

const dateFormat = "DD-MM-YYYY"
const isParseable = (expression: ExpressionObj): boolean => {
  const date = spelFormatters[SpelFormatterType.Date].decode(expression.expression)
  return date && moment(date, dateFormat).isValid()
}

export function DateEditor(props: Omit<DatepickerEditorProps, "dateFormat" | "expressionType">) {

  const {formatter} = props

  return (
    <DatepickerEditor
      {...props}
      momentFormat={dateFormat}
      dateFormat={dateFormat}
      timeFormat={null}
      formatter={formatter || spelFormatters[SpelFormatterType.Date]}
    />
  )
}

DateEditor.switchableToHint = () => i18next.t("editors.LocalDate.switchableToHint", "Switch to basic mode")
DateEditor.notSwitchableToHint = () => i18next.t("editors.LocalDate.notSwitchableToHint", "Expression must be valid date to switch to basic mode")
DateEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression)

