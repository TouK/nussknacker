import {DurationComponentType} from "./DurationEditor"
import {ExpressionObj} from "../types"
import {Validator} from "../../Validators"
import React from "react"
import moment from "moment"
import TimeRangeEditor from "./TimeRangeEditor"
import _ from "lodash";

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
const UNDEFINED_PERIOD = {
  years: () => undefined,
  months: () => undefined,
  days: () => undefined
}

export default function PeriodEditor(props: Props) {

  const {expressionObj, onValueChange, validators, showValidation, readOnly, isMarked} = props

  function isPeriodDefined(period: Period): boolean {
    return period.years !== undefined || period.months !== undefined || period.days !== undefined
  }

  function encode(period: Period): string {
    return isPeriodDefined(period) ? SPEL_FORMATTED_PERIOD(moment.duration(period).toISOString()) : ""
  }

  function decode(expression: string): Period {
    const regexExecution = SPEL_PERIOD_DECODE_REGEX.exec(expression);
    const period = regexExecution === null ? UNDEFINED_PERIOD : moment.duration(regexExecution[1])
    return {
      years: period.years(),
      months: period.months(),
      days: period.days(),
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

PeriodEditor.switchableTo = (expressionObj: ExpressionObj) =>
  SPEL_PERIOD_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || _.isEmpty(expressionObj.expression)

PeriodEditor.switchableToHint = "Switch to basic mode"

PeriodEditor.notSwitchableToHint = "Expression must match pattern T(java.time.Period).parse('P(n)Y(n)M(n)W(n)D') to switch to basic mode"