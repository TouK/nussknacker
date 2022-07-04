import {ExpressionObj} from "../types"
import {Validator} from "../../Validators"
import React, {useCallback, useMemo} from "react"
import moment from "moment"
import TimeRangeEditor from "./TimeRangeEditor"
import _ from "lodash"
import i18next from "i18next"
import {Formatter, FormatterType, typeFormatters} from "../Formatter"

export type Period = {
  years: number,
  months: number,
  days: number,
}

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: (value: string) => void,
  validators: Array<Validator>,
  showValidation?: boolean,
  readOnly: boolean,
  isMarked: boolean,
  editorConfig: $TodoType,
  formatter: Formatter,
}

const SPEL_PERIOD_SWITCHABLE_TO_REGEX = /^T\(java\.time\.Period\)\.parse\('(-)?P([0-9]{1,}Y)?((-)?[0-9]{1,}M)?((-)?[0-9]{1,}W)?((-)?[0-9]{1,}D)?'\)$/
const NONE_PERIOD = {
  years: () => null,
  months: () => null,
  days: () => null,
}

export default function PeriodEditor(props: Props): JSX.Element {

  const {expressionObj, onValueChange, validators, showValidation, readOnly, isMarked, editorConfig, formatter} = props

  const periodFormatter = useMemo(
    () => formatter == null ? typeFormatters[FormatterType.Period] : formatter,
    [formatter]
  )

  const isValueNotNullAndNotZero = useCallback(
    (value: number) => value != null && value != 0,
    []
  )

  const isPeriodDefined = useCallback(
    (period: Period): boolean => isValueNotNullAndNotZero(period.years) ||
      isValueNotNullAndNotZero(period.months) ||
      isValueNotNullAndNotZero(period.days),
    [isValueNotNullAndNotZero]
  )
  const encode = useCallback(
    (period: Period): string => isPeriodDefined(period) ? periodFormatter.encode(period) : "",
    [isPeriodDefined, periodFormatter]
  )

  const decode = useCallback(
    (expression: string): Period => {
      const result = periodFormatter.decode(expression)
      const period = result == null ? NONE_PERIOD : moment.duration(result)
      return {
        years: period.years(),
        months: period.months(),
        days: period.days(),
      }
    },
    [periodFormatter]
  )

  return (
    <TimeRangeEditor
      encode={encode}
      decode={decode}
      onValueChange={onValueChange}
      editorConfig={editorConfig}
      readOnly={readOnly}
      showValidation={showValidation}
      validators={validators}
      expression={expressionObj.expression}
      isMarked={isMarked}
    />
  )
}

PeriodEditor.switchableTo = (expressionObj: ExpressionObj) => SPEL_PERIOD_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || _.isEmpty(expressionObj.expression)

PeriodEditor.switchableToHint = () => i18next.t("editors.period.switchableToHint", "Switch to basic mode")

PeriodEditor.notSwitchableToHint = () => i18next.t("editors.period.notSwitchableToHint",
  "Expression must match pattern T(java.time.Period).parse('P(n)Y(n)M(n)W(n)D') to switch to basic mode")
