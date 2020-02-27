import React from "react"
import {ExpressionObj} from "../types"
import moment from "moment"
import {Validator} from "../../Validators"
import "./timeRange.styl"
import TimeRangeEditor from "./TimeRangeEditor"
import _ from "lodash"
import i18next from "i18next"
import {TimeRangeComponentType} from "./TimeRangeComponent"

export type Duration = {
  days: number,
  hours: number,
  minutes: number,
}

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: Function,
  validators: Array<Validator>,
  showValidation?: boolean,
  readOnly: boolean,
  isMarked: boolean,
  components: Array<TimeRangeComponentType>,
}

const SPEL_DURATION_DECODE_REGEX = /^T\(java\.time\.Duration\)\.parse\('(.*?)'\)$/
const SPEL_DURATION_SWITCHABLE_TO_REGEX = /^T\(java\.time\.Duration\)\.parse\('P([0-9]{1,}D)?(T([0-9]{1,}H)?([0-9]{1,}M)?)?'\)$/
const SPEL_FORMATTED_DURATION = (isoDuration) => `T(java.time.Duration).parse('${isoDuration}')`
const NONE_DURATION = {
  days: () => null,
  hours: () => null,
  minutes: () => null,
}

export default function DurationEditor(props: Props) {

  const {expressionObj, onValueChange, validators, showValidation, readOnly, isMarked, components} = props

  function isDurationDefined(value: Duration) {
    return value.days != null || value.hours != null || value.minutes != null
  }

  function encode(value: Duration): string {
    return isDurationDefined(value) ? SPEL_FORMATTED_DURATION(moment.duration(value).toISOString()) : ""
  }

  function decode(expression: string): Duration {
    const regexExec = SPEL_DURATION_DECODE_REGEX.exec(expression)
    const duration = regexExec == null ? NONE_DURATION : moment.duration(regexExec[1])
    return {
      days: duration.days(),
      hours: duration.hours(),
      minutes: duration.minutes(),
    }
  }

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

DurationEditor.switchableTo = (expressionObj: ExpressionObj) =>
  SPEL_DURATION_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || _.isEmpty(expressionObj.expression)

DurationEditor.switchableToHint = () => i18next.t("editors.duration.switchableToHint", "Switch to basic mode")

DurationEditor.notSwitchableToHint = () => i18next.t("editors.duration.noSwitchableToHint",
  "Expression must match pattern T(java.time.Duration).parse('P(n)DT(n)H(n)M') to switch to basic mode")
