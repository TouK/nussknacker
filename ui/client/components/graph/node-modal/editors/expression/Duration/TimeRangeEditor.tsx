import React, {useEffect, useState} from "react"
import {UnknownFunction} from "../../../../../../types/common"
import {Period} from "./PeriodEditor"
import TimeRangeSection from "./TimeRangeSection"
import {Validator} from "../../Validators"
import {TimeRange} from "./TimeRangeComponent"
import {Duration} from "./DurationEditor"

type Props = {
  encode: UnknownFunction,
  decode: ((exp: string) => Duration)|((exp: string) => Period),
  onValueChange: UnknownFunction,
  editorConfig: $TodoType,
  readOnly: boolean,
  showValidation: boolean,
  validators: Array<Validator>,
  expression: string,
  isMarked: boolean,
}

export default function TimeRangeEditor(props: Props) {

  const {encode, decode, onValueChange, editorConfig, readOnly, showValidation, validators, expression, isMarked} = props

  const components = editorConfig.timeRangeComponents as Array<TimeRange>
  const [value, setValue] = useState(decode(expression))

  const onComponentChange = (fieldName: string, fieldValue: number) => {
    setValue(
      {
        ...value,
        [fieldName]: isNaN(fieldValue) ? null : fieldValue,
      }
    )
  }

  useEffect(
    () => {
      onValueChange(encode(value))
    },
    [value],
  )

  return (
    <TimeRangeSection
      components={components}
      onComponentValueChange={onComponentChange}
      readOnly={readOnly}
      showValidation={showValidation}
      validators={validators}
      value={value}
      expression={expression}
      isMarked={isMarked}
    />
  )
}
