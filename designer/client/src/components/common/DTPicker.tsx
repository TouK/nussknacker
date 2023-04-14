import DateTimePicker from "react-datetime"
import {useTranslation} from "react-i18next"
import React from "react"
import "./DTPicker.css"

export function DTPicker({
  dateFormat,
  timeFormat,
  inputProps,
  onChange,
  value,
}: DateTimePicker.DatetimepickerProps): JSX.Element {
  const {i18n} = useTranslation()
  return (
    <DateTimePicker
      dateFormat={dateFormat}
      timeFormat={timeFormat}
      inputProps={inputProps}
      onChange={onChange}
      value={value}
      locale={i18n.language}
    />
  )
}
