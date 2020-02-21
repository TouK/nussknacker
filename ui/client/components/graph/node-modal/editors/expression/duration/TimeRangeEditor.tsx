import React, {useEffect, useState} from "react"
import TimeRangeSection from "./TimeRangeSection"
import {DurationComponentType} from "./DurationEditor"
import {Validator} from "../../Validators"

type Props = {
  encode: Function,
  decode: Function,
  onValueChange: Function,
  components: Array<DurationComponentType>,
  readOnly: boolean,
  showValidation: boolean,
  validators: Array<Validator>,
  expression: string,
  isMarked: boolean,
}

export default function TimeRangeEditor(props: Props) {

  const {encode, decode, onValueChange, components, readOnly, showValidation, validators, expression, isMarked} = props

  const [value, setValue] = useState(decode(expression))

  const onComponentChange = (fieldName: string, fieldValue: number) => {
    setValue(
      {
        ...value,
        [fieldName]: isNaN(fieldValue) ? undefined : fieldValue
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