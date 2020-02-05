import React, {useState, useEffect} from "react"
import {ExpressionObj} from "../types"
import {useTranslation} from "react-i18next"
import DateTimePicker from "react-datetime"
import "react-datetime/css/react-datetime.css"
import classNames from "classnames"
import {useDebouncedCallback} from "use-debounce"
import moment from "moment"
import {asLocalDateString} from "./DateEditor"
import {asLocalTimeString} from "./TimeEditor"
import {asLocalDateTimeString} from "./DateTimeEditor"
import {allValid, Validator} from "../../../../../../common/Validators"
import ValidationLabels from "../../../../../modals/ValidationLabels"
import i18next from "i18next"

/* eslint-disable i18next/no-literal-string */
export enum JavaTimeTypes {
  LOCAL_DATE = "LocalDate",
  LOCAL_DATE_TIME = "LocalDateTime",
  LOCAL_TIME = "LocalTime",
}

/* eslint-enable i18next/no-literal-string */

export type DatepickerEditorProps = {
  expressionObj: ExpressionObj,
  readOnly: boolean,
  className: string,
  onValueChange,
  validators: Validator[],
  showValidation: boolean,
  isMarked: boolean,
  editorFocused: boolean,
  expressionType: JavaTimeTypes,
  dateFormat: string,
  timeFormat?: string,
}

const parse = ({expression}: ExpressionObj, expressionType: JavaTimeTypes): moment.Moment | null => {
  const parseRegExp = i18next.t("expressions:date.parse.regExp", "^T\\(java\\.time\\.(.*)\\)\\.parse\\(['\"](.*)['\"]\\)$")
  const [fullString, type, date] = new RegExp(parseRegExp).exec(expression) || []
  const formats = expressionType === JavaTimeTypes.LOCAL_TIME ?
    i18next.t("expressions:date.parse.timeOnlyFormat", "HH:mm:ss") :
    i18next.t("expressions:date.parse.dateTimeFormat", "YYYY-MM-DDTHH:mm:ss")

  return moment(date, formats) || null
}

function format(value: string | moment.Moment, expressionType: JavaTimeTypes): string {
  const m = moment(value)
  debugger
  if (m.isValid()) {
    switch (expressionType) {
      case JavaTimeTypes.LOCAL_DATE_TIME:
        return asLocalDateTimeString(m)
      case JavaTimeTypes.LOCAL_DATE:
        return asLocalDateString(m)
      case JavaTimeTypes.LOCAL_TIME:
        return asLocalTimeString(m)
    }
  }
  return ""
}

export const isParseable = (expression: ExpressionObj, expressionType: JavaTimeTypes): boolean => {
  const date = parse(expression, expressionType)
  return date && date.isValid()
}

const getDateValidator = (value: string | moment.Moment, expressionType: JavaTimeTypes): Validator => ({
  description: i18next.t("validation.wrongDateFormat", "wrong_date_format"),
  message: i18next.t("validation.wrongDateFormat", "wrong_date_format"),
  isValid: () => !!format(value, expressionType),
})

export function DatepickerEditor(props: DatepickerEditorProps) {
  const {i18n} = useTranslation()
  const {className, expressionObj, onValueChange, expressionType, readOnly, validators, showValidation, isMarked, editorFocused, ...other} = props
  const [value, setValue] = useState<string | moment.Moment>(moment(parse(expressionObj, expressionType)))
  const {expression} = expressionObj
  const [onChange] = useDebouncedCallback(
    value => {
      const date = format(value, expressionType)
      onValueChange(date)
    },
    200,
  )

  useEffect(
    () => {
      onChange(value)
    },
    [value],
  )

  const isValid = allValid(validators, [expression])

  const localValidators = [
    getDateValidator(value, expressionType),
  ]

  return (
    <div
      className={className}
    >
      <DateTimePicker
        onChange={setValue}
        value={value}
        inputProps={{
          className: classNames([
            "node-input",
            showValidation && !isValid && "node-input-with-error",
            isMarked && "marked",
            editorFocused && "focused",
            readOnly && "read-only",
          ]),
          readOnly,
          disabled: readOnly,
        }}
        locale={i18n.language}
        {...other}
      />
      {showValidation && (
        <ValidationLabels
          validators={[
            ...localValidators,
            ...validators,
          ]}
          values={[expression]}
        />
      )}
    </div>
  )
}

