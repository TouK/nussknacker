import React from "react";
import './timeRange.styl'
import classNames from "classnames";

type Props = {
  label: string,
  onChange: Function,
  value: number,
  readOnly: boolean,
  showValidation: boolean,
  isValid: boolean,
  isMarked: boolean,
}

export default function TimeRangeComponent(props: Props) {

  const {label, onChange, value, readOnly, showValidation, isValid, isMarked} = props

  return (
    <div className={"time-range-component"}>
      <input
        readOnly={readOnly}
        value={value}
        onChange={(event) => onChange(parseInt(event.target.value))}
        className={classNames([
          "time-range-input",
          showValidation && !isValid && "node-input-with-error",
          isMarked && "marked",
          readOnly && "read-only",
        ])}
        type={"number"}
      />
      <span className={"time-range-component-label"}>{label}</span>
    </div>
  )
}