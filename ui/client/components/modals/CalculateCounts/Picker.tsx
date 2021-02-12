import {MomentInput} from "moment"
import React from "react"
import DateTimePicker from "react-datetime"

const datePickerStyle = {
  // eslint-disable-next-line i18next/no-literal-string
  className: "node-input",
}

const dateFormat = "YYYY-MM-DD"
const timeFormat = "HH:mm:ss"

type PickerProps = {label: string, onChange: (date: MomentInput) => void, value: Date}

export function Picker({label, onChange, value}: PickerProps): JSX.Element {
  return (
    <>
      <p>{label}</p>
      <div className="datePickerContainer">
        <DateTimePicker
          dateFormat={dateFormat}
          timeFormat={timeFormat}
          inputProps={datePickerStyle}
          onChange={onChange}
          value={value}
        />
      </div>
    </>
  )
}
