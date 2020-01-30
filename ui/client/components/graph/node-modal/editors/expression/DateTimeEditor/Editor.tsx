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

export type EditorProps = {
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

const parse = ({expression}: ExpressionObj, expressionType: JavaTimeTypes): moment.Moment => {
  const [e, type, date] = /^T\(java\.time\.(.*)\)\.parse\([\'\"](.*)[\'\"]\)$/.exec(expression) || []
  if (type === expressionType) {
    return moment(date, [moment.HTML5_FMT.DATETIME_LOCAL_SECONDS, moment.HTML5_FMT.TIME_SECONDS])
  }
  return null
}

function format(value: string | moment.Moment, expressionType: JavaTimeTypes): string {
  const m = moment(value)
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

export function Editor(props: EditorProps) {
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
        {showValidation && <ValidationLabels validators={[
          ...localValidators,
          ...validators,
        ]} values={[expression]}/>}
      </div>
  )
}

