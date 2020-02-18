import React from "react"
import {ExpressionObj} from "../types"
import moment from "moment"
import {Validator} from "../../Validators"
import './timeRange.styl'
import TimeRangeEditor from "./TimeRangeEditor"

export type Duration = {
  days: number,
  hours: number,
  minutes: number,
}

export type DurationComponentType = {
  label: string,
  fieldName: string,
}

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: Function,
  validators: Array<Validator>,
  components: Array<DurationComponentType>,
  showValidation?: boolean,
  readOnly: boolean,
  isMarked: boolean,
}

const SPEL_DURATION_DECODE_REGEX = /^T\(java\.time\.Duration\)\.parse\('(.*?)'\)$/
const SPEL_DURATION_SWITCHABLE_TO_REGEX = /^T\(java\.time\.Duration\)\.parse\('P([0-9]{1,}D)?(T([0-9]{1,}H)?([0-9]{1,}M)?)?'\)$/
const SPEL_FORMATTED_DURATION = (isoDuration) => `T(java.time.Duration).parse('${isoDuration}')`;

export default function DurationEditor(props: Props) {

  const {expressionObj, onValueChange, validators, showValidation, readOnly, isMarked} = props

  function encode(value: Duration): string {
    const isoFormattedDuration = moment.duration(value).toISOString()
    return SPEL_FORMATTED_DURATION(isoFormattedDuration)
  }

  function decode(expression: string): Duration {
    const isoFormattedDuration = SPEL_DURATION_DECODE_REGEX.exec(expression)[1]
    const duration = moment.duration(isoFormattedDuration)
    // @ts-ignore
    return {
      days: duration._data.days,
      hours: duration._data.hours,
      minutes: duration._data.minutes,
    }
  }

  const components: Array<DurationComponentType> = [
    {
      label: "d",
      fieldName: "days"
    },
    {
      label: "h",
      fieldName: "hours",
    },
    {
      label: "m",
      fieldName: "minutes"
    },
  ]

  return (
    <TimeRangeEditor
      encode={encode}
      decode={decode}
      onValueChange={onValueChange}
      components={components}
      readOnly={readOnly}
      showValidation={showValidation}
      validators={validators}
      expression={expressionObj.expression}
      isMarked={isMarked}
    />
  )
}

DurationEditor.switchableTo = (expressionObj: ExpressionObj) => SPEL_DURATION_SWITCHABLE_TO_REGEX.test(expressionObj.expression)
DurationEditor.switchableToHint = "Switch to basic mode"
DurationEditor.notSwitchableToHint = "Expression must match pattern T(java.time.Duration).parse('P(n)DT(n)H(n)M') to switch to basic mode"
