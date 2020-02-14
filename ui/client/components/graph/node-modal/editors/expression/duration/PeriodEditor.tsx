import {DurationComponentType} from "./DurationEditor";
import {ExpressionObj} from "../types";
import {Validator} from "../../Validators";
import React from "react";
import moment from "moment";
import TimeRangeEditor from "./TimeRangeEditor";

export type Period = {
  years: number,
  months: number,
  weeks: number,
  days: number
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
    // @ts-ignore
    return {
      years: period._data.years || 0,
      months: period._data.months || 0,
      weeks: period._data.weeks || 0,
      days: period._data.days || 0,
    }
  }

  const components: Array<DurationComponentType> = [
    {
      label: "y",
      fieldName: "years"
    },
    {
      label: "m",
      fieldName: "months",
    },
    {
      label: "w",
      fieldName: "weeks"
    },
    {
      label: "d",
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