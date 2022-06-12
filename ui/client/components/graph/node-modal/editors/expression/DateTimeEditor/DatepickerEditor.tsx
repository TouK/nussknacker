import React, {useEffect, useState} from "react"
import {ExpressionObj} from "../types"
import classNames from "classnames"
import {useDebouncedCallback} from "use-debounce"
import moment from "moment"
import ValidationLabels from "../../../../../modals/ValidationLabels"
import i18next from "i18next"
import {allValid, HandledErrorType, Validator, ValidatorType} from "../../Validators"
import {Formatter} from "../Formatter"
import {DTPicker} from "../../../../../common/DTPicker"

/* eslint-disable i18next/no-literal-string */
export enum JavaTimeTypes {
  LOCAL_DATE_TIME = "LocalDateTime",
}

export type DatepickerEditorProps = {
  expressionObj: ExpressionObj,
  readOnly: boolean,
  className: string,
  onValueChange: (value: string) => void,
  validators: Validator[],
  showValidation: boolean,
  isMarked: boolean,
  editorFocused: boolean,
  formatter: Formatter,
  momentFormat: string,
  dateFormat?: string,
  timeFormat?: string,
}

export function DatepickerEditor(props: DatepickerEditorProps) {
  const {
    className, expressionObj, onValueChange, readOnly, validators, showValidation, isMarked,
    editorFocused, formatter, momentFormat, ...other
  } = props

  function encode(value: string | moment.Moment): string {
    const m = moment(value, momentFormat)
    if (m.isValid()) {
      return formatter.encode(m)
    }
    return ""
  }

  const decode = (expression): moment.Moment | null => {
    const date = formatter.decode(expression)
    const m = moment(date, momentFormat)
    return m.isValid() ? m : null
  }

  const {expression} = expressionObj
  const [value, setValue] = useState<string | moment.Moment>(decode(expression) == null ? null : decode(expression))
  const [onChange] = useDebouncedCallback<[value: string | moment.Moment]>(
    value => {
      const encoded = encode(value)
      onValueChange(encoded)
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

  const getDateValidator = (value: string | moment.Moment): Validator => ({
    description: () => i18next.t("validation.wrongDateFormat", "Wrong date format"),
    message: () => i18next.t("validation.wrongDateFormat", "Wrong date format"),
    isValid: () => !value || !!encode(value),
    validatorType: ValidatorType.Frontend,
    handledErrorType: HandledErrorType.WrongDateFormat,
  })

  const localValidators = [
    getDateValidator(value),
  ]

  return (
    <div
      className={className}
    >
      <DTPicker
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

