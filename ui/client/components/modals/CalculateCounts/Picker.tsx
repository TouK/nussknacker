import {Moment} from "moment"
import React from "react"
import {useTranslation} from "react-i18next"
import {DTPicker} from "../../common/DTPicker"

const datePickerStyle = {
  // eslint-disable-next-line i18next/no-literal-string
  className: "node-input",
}

const dateFormat = "YYYY-MM-DD"
const timeFormat = "HH:mm:ss"

export type PickerInput = Moment | string

type PickerProps = {label: string, onChange: (date: PickerInput) => void, value: PickerInput }

export function Picker({label, onChange, value}: PickerProps): JSX.Element {
  const {i18n} = useTranslation()
  return (
    <>
      <p>{label}</p>
      <div className="datePickerContainer">
        <DTPicker
          dateFormat={dateFormat}
          timeFormat={timeFormat}
          inputProps={datePickerStyle}
          onChange={onChange}
          value={value}
          locale={i18n.language}
        />
      </div>
    </>
  )
}
