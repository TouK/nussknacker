import React, {useCallback, useMemo} from "react"
import {ExpressionObj} from "../types"
import {Validator} from "../../Validators"
import "./timeRange.styl"
import TimeRangeEditor from "./TimeRangeEditor"
import _ from "lodash"
import i18next from "i18next"
import {Formatter, FormatterType, typeFormatters} from "../Formatter"
import moment from "moment"

export type Duration = {
  days: number,
  hours: number,
  minutes: number,
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

const SPEL_DURATION_SWITCHABLE_TO_REGEX = /^T\(java\.time\.Duration\)\.parse\('(-)?P([0-9]{1,}D)?(T((-)?[0-9]{1,}H)?((-)?[0-9]{1,}M)?)?'\)$/
const NONE_DURATION = {
  days: () => null,
  hours: () => null,
  minutes: () => null,
}

export default function DurationEditor(props: Props): JSX.Element {
  const {expressionObj, onValueChange, validators, showValidation, readOnly, isMarked, editorConfig, formatter} = props

  const durationFormatter = useMemo(
    () => formatter == null ? typeFormatters[FormatterType.Duration] : formatter,
    [formatter]
  )

  const isValueNotNullAndNotZero = useCallback(
    (value: number) => value != null && value != 0,
    []
  )

  const isDurationDefined = useCallback(
    (value: Duration) => isValueNotNullAndNotZero(value.days) ||
      isValueNotNullAndNotZero(value.hours) ||
      isValueNotNullAndNotZero(value.minutes),
    [isValueNotNullAndNotZero]
  )

  const encode = useCallback(
    (value: Duration): string => isDurationDefined(value) ? durationFormatter.encode(value) : "",
    [durationFormatter, isDurationDefined]
  )

  const decode = useCallback(
    (expression: string): Duration => {
      const decodeExecResult = durationFormatter.decode(expression)
      const duration = decodeExecResult == null ? NONE_DURATION : moment.duration(decodeExecResult)
      return {
        days: duration.days(),
        hours: duration.hours(),
        minutes: duration.minutes(),
      }
    },
    [durationFormatter]
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

DurationEditor.switchableTo = (expressionObj: ExpressionObj) => SPEL_DURATION_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || _.isEmpty(expressionObj.expression)

DurationEditor.switchableToHint = () => i18next.t("editors.duration.switchableToHint", "Switch to basic mode")

DurationEditor.notSwitchableToHint = () => i18next.t("editors.duration.noSwitchableToHint",
  "Expression must match pattern T(java.time.Duration).parse('P(n)DT(n)H(n)M') to switch to basic mode")
