import {useTranslation} from "react-i18next"
import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {DatepickerEditorProps, DatepickerEditor, JavaTimeTypes, isParseable} from "./DatepickerEditor"
import {isEmpty} from "lodash"

export function DateTimeEditor(props: Omit<DatepickerEditorProps, "dateFormat" | "expressionType">) {
  const {i18n} = useTranslation()
  const dateFormat = i18n.t("editors.LocalDateTime.dateFormat", "DD-MM-YYYY")
  const timeFormat = i18n.t("editors.LocalDateTime.timeFormat", "HH:mm")
  return <DatepickerEditor {...props} dateFormat={dateFormat} timeFormat={timeFormat} expressionType={JavaTimeTypes.LOCAL_DATE_TIME}/>
}

DateTimeEditor.switchableToHint = i18next.t("editors.LocalDateTime.switchableToHint", "Switch to basic mode")
DateTimeEditor.notSwitchableToHint = i18next.t(
    "editors.LocalDateTime.notSwitchableToHint",
    "Expression must be valid dateTime to switch to basic mode",
)
DateTimeEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj, JavaTimeTypes.LOCAL_DATE_TIME) || isEmpty(
    expressionObj.expression,
)

