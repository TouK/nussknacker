import React from "react"
import "./timeRange.styl"
import classNames from "classnames"
import {UnknownFunction} from "../../../../../../types/common"
import {InputWithFocus} from "../../../../../withFocus"
import {Duration} from "./DurationEditor"
import {Period} from "./PeriodEditor"

export type TimeRangeComponentType = {
  label: string,
  fieldName: string,
}

export enum TimeRange {
  Years = "YEARS",
  Months = "MONTHS",
  Days = "DAYS",
  Hours = "HOURS",
  Minutes = "MINUTES"
}

const components: Record<string, TimeRangeComponentType> = {
  [TimeRange.Years]: {
    label: "years",
    fieldName: "years",
  },
  [TimeRange.Months]: {
    label: "months",
    fieldName: "months",
  },
  [TimeRange.Days]: {
    label: "days",
    fieldName: "days",
  },
  [TimeRange.Hours]: {
    label: "hours",
    fieldName: "hours",
  },
  [TimeRange.Minutes]: {
    label: "minutes",
    fieldName: "minutes",
  },
}

type Props = {
  timeRangeComponentName: TimeRange,
  onChange: UnknownFunction,
  value: Duration | Period,
  readOnly: boolean,
  showValidation: boolean,
  isValid: boolean,
  isMarked: boolean,
}

export default function TimeRangeComponent(props: Props) {

  const {timeRangeComponentName, onChange, value, readOnly, showValidation, isValid, isMarked} = props
  const component = components[timeRangeComponentName]

  return (
    <div className={"time-range-component"}>
      <InputWithFocus
        readOnly={readOnly}
        value={value[component.fieldName] || ""}
        onChange={(event) => onChange(component.fieldName, parseInt(event.target.value))}
        className={classNames([
          "time-range-input",
          showValidation && !isValid && "node-input-with-error",
          isMarked && "marked",
          readOnly && "read-only",
        ])}
        type={"number"}
      />
      <span className={"time-range-component-label"}>{component.label}</span>
    </div>
  )
}
