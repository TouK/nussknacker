import {useTranslation} from "react-i18next"
import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {isEmpty} from "lodash"
import {DatepickerEditorProps, DatepickerEditor, isParseable, JavaTimeTypes} from "./DatepickerEditor"

export function DateEditor(props: Omit<DatepickerEditorProps, "dateFormat" | "expressionType">) {
  const {i18n} = useTranslation()
  const dateFormat = i18n.t("editors.LocalDate.dateFormat", "DD-MM-YYYY")
  return <DatepickerEditor {...props} dateFormat={dateFormat} timeFormat={null} expressionType={JavaTimeTypes.LOCAL_DATE}/>
}

DateEditor.switchableToHint = () =>  i18next.t("editors.LocalDate.switchableToHint", "Switch to basic mode")
DateEditor.notSwitchableToHint = () => i18next.t("editors.LocalDate.notSwitchableToHint", "Expression must be valid date to switch to basic mode")
DateEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj, JavaTimeTypes.LOCAL_DATE) || isEmpty(
    expressionObj.expression,
)

