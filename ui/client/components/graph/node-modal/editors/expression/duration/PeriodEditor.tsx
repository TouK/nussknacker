import {DurationComponentType} from "./DurationEditor"
import {ExpressionObj} from "../types"
import {Validator} from "../../Validators"
import React from "react"
import moment from "moment"
import TimeRangeEditor from "./TimeRangeEditor"

export type Period = {
  years: number,
  months: number,
  days: number,
}

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: Function,
  validators: Array<Validator>,
  showValidation?: boolean,
  readOnly: boolean,
  isMarked: boolean,
}

const SPEL_PERIOD_DECODE_REGEX = /^T\(java\.time\.Period\)\.parse\('(.*?)'\)$/
const SPEL_PERIOD_SWITCHABLE_TO_REGEX = /^T\(java\.time\.Period\)\.parse\('P([0-9]{1,}Y)?([0-9]{1,}M)?([0-9]{1,}W)?([0-9]{1,}D)?'\)$/
const SPEL_FORMATTED_PERIOD = (isoPeriod) => `T(java.time.Period).parse('${isoPeriod}')`

export default function PeriodEditor(props: Props) {

  const {expressionObj, onValueChange, validators, showValidation, readOnly, isMarked} = props

  function encode(period: Period): string {
    const isoFormattedPeriod = moment.duration(period).toISOString()
    return SPEL_FORMATTED_PERIOD(isoFormattedPeriod)
  }

  function decode(expression: string): Period {
    const isoFormattedPeriod = SPEL_PERIOD_DECODE_REGEX.exec(expression)[1]
    const period = moment.duration(isoFormattedPeriod)
    return {
      years: period.years() || 0,
      months: period.months() || 0,
      days: period.days() || 0,
    }
  }

  const components: Array<DurationComponentType> = [
    {
      label: "years",
      fieldName: "years"
    },
    {
      label: "months",
      fieldName: "months",
    },
    {
      label: "days",
      fieldName: "days"
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

PeriodEditor.switchableTo = (expressionObj: ExpressionObj) => SPEL_PERIOD_SWITCHABLE_TO_REGEX.test(expressionObj.expression)
PeriodEditor.switchableToHint = "Switch to basic mode"
PeriodEditor.notSwitchableToHint = "Expression must match pattern T(java.time.Period).parse('P(n)Y(n)M(n)W(n)D') to switch to basic mode"