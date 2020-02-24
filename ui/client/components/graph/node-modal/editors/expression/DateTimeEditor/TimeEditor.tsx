import {useTranslation} from "react-i18next"
import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {isEmpty} from "lodash"
import {DatepickerEditor, JavaTimeTypes, isParseable, DatepickerEditorProps} from "./DatepickerEditor"

export function TimeEditor(props: Omit<DatepickerEditorProps, "dateFormat" | "expressionType">) {
  const {i18n} = useTranslation()
  const timeFormat = i18n.t("editors.LocalTime.timeFormat", "HH:mm:ss")
  return <DatepickerEditor {...props} dateFormat={null} timeFormat={timeFormat} expressionType={JavaTimeTypes.LOCAL_TIME}/>
}

TimeEditor.switchableToHint = () => i18next.t("editors.LocalTime.switchableToHint", "Switch to basic mode")
TimeEditor.notSwitchableToHint = () => i18next.t("editors.LocalTime.notSwitchableToHint", "Expression must be valid time to switch to basic mode")
TimeEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj, JavaTimeTypes.LOCAL_TIME) || isEmpty(
    expressionObj.expression,
)

