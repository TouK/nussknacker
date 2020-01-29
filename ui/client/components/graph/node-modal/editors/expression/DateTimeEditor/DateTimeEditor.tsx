import {useTranslation} from "react-i18next"
import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {EditorProps, Editor, JavaTimeTypes, isParseable} from "./Editor"
import {isEmpty} from "lodash"
import moment from "moment"

export function DateTimeEditor(props: Omit<EditorProps, "dateFormat" | "expressionType">) {
  const {i18n} = useTranslation()
  const dateFormat = i18n.t("editors.LocalDateTime.dateFormat", "DD-MM-YYYY")
  const timeFormat = i18n.t("editors.LocalDateTime.timeFormat", "H:mm")
  return <Editor {...props} dateFormat={dateFormat} timeFormat={timeFormat} expressionType={JavaTimeTypes.LOCAL_DATE_TIME}/>
}

DateTimeEditor.switchableToHint = i18next.t("editors.LocalDateTime.switchableToHint", "Switch to basic mode")
DateTimeEditor.notSwitchableToHint = i18next.t(
    "editors.LocalDateTime.notSwitchableToHint",
    "Expression must be valid dateTime to switch to basic mode",
)
DateTimeEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj, JavaTimeTypes.LOCAL_DATE_TIME) || isEmpty(
    expressionObj.expression)

export function asLocalDateTimeString(m: moment.Moment) {
  return i18next.t(
      "expressions:LocalDateTime",
      "T(java.time.LocalDateTime).parse('{{date, YYYY-MM-DDTHH:mm}}')",
      {date: m.startOf("minute")}, // eslint-disable-line i18next/no-literal-string
  )
}
