import {useTranslation} from "react-i18next"
import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {EditorProps, Editor, JavaTimeTypes, isParseable} from "./Editor"
import {isEmpty} from "lodash"
import moment from "moment"

export function DateEditor(props: Omit<EditorProps, "dateFormat" | "expressionType">) {
  const {i18n} = useTranslation()
  const dateFormat = i18n.t("editors.LocalDate.dateFormat", "DD-MM-YYYY")
  return <Editor {...props} dateFormat={dateFormat} timeFormat={null} expressionType={JavaTimeTypes.LOCAL_DATE}/>
}

DateEditor.switchableToHint = i18next.t("editors.LocalDate.switchableToHint", "Switch to basic mode")
DateEditor.notSwitchableToHint = i18next.t("editors.LocalDate.notSwitchableToHint", "Expression must be valid date to switch to basic mode")
DateEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj, JavaTimeTypes.LOCAL_DATE) || isEmpty(
  expressionObj.expression
)

export function asLocalDateString(m: moment.Moment) {
  return i18next.t(
    "expressions:LocalDate",
    "T(java.time.LocalDate).parse('{{date, YYYY-MM-DD}}')",
    {date: m.startOf("day")}, // eslint-disable-line i18next/no-literal-string
  )
}
