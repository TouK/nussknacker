import {useTranslation} from "react-i18next"
import i18next from "i18next"
import {ExpressionObj} from "../types"
import React from "react"
import {isEmpty} from "lodash"
import {Editor, EditorProps, JavaTimeTypes, isParseable} from "./Editor"
import moment from "moment"

export function TimeEditor(props: Omit<EditorProps, "dateFormat" | "expressionType">) {
  const {i18n} = useTranslation()
  const timeFormat = i18n.t("editors.LocalTime.timeFormat", "H:mm:ss")
  return <Editor {...props} dateFormat={null} timeFormat={timeFormat} expressionType={JavaTimeTypes.LOCAL_TIME}/>
}

TimeEditor.switchableToHint = i18next.t("editors.LocalTime.switchableToHint", "Switch to basic mode")
TimeEditor.notSwitchableToHint = i18next.t("editors.LocalTime.notSwitchableToHint", "Expression must be valid time to switch to basic mode")
TimeEditor.switchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj, JavaTimeTypes.LOCAL_TIME) || isEmpty(
  expressionObj.expression
)

export function asLocalTimeString(m: moment.Moment) {
  return i18next.t(
    "expressions:LocalTime",
    "T(java.time.LocalTime).parse('{{date, HH:mm:ss}}')",
    {date: m.startOf("second")}, // eslint-disable-line i18next/no-literal-string
  )
}
