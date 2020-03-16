import i18next from "i18next"
import {isEmpty} from "lodash"
import React from "react"
import FixedValuesEditor from "./FixedValuesEditor"
import {ExpressionObj} from "./types"
import {Editor} from "./Editor"

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: Function,
  readOnly: boolean,
  className: string,
  values?: $TodoType,
}

const SUPPORTED_LANGUAGE = "spel"
const TRUE_EXPRESSION = "true"
const FALSE_EXPRESSION = "false"

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return (expression === "true" || expression === "false") && language === SUPPORTED_LANGUAGE
}

const BoolEditor: Editor<Props> = (props: Props) => {
  const {expressionObj, readOnly, onValueChange, className} = props

  const trueValue = {expression: TRUE_EXPRESSION, label: i18next.t("common.true", "true")}
  const falseValue = {expression: FALSE_EXPRESSION, label: i18next.t("common.false", "false")}

  return (
    <FixedValuesEditor
      values={[
        trueValue,
        falseValue,
      ]}
      defaultValue={trueValue}
      expressionObj={expressionObj}
      onValueChange={onValueChange}
      readOnly={readOnly}
      className={className}
      validators={[]}
      showValidation={true}
    />
  )
}

export default BoolEditor

BoolEditor.switchableTo = (expressionObj) => parseable(expressionObj) || isEmpty(expressionObj.expression)
BoolEditor.switchableToHint = () => i18next.t("editors.bool.switchableToHint", "Switch to basic mode")
BoolEditor.notSwitchableToHint = () =>  i18next.t("editors.bool.notSwitchableToHint",
  "Expression must be equal to true or false to switch to basic mode")

