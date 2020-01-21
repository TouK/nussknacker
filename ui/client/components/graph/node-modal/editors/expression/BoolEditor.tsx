import i18next from "i18next"
import {isEmpty} from "lodash"
import React, {ReactNode} from "react"
import ExpressionWithFixedValues from "./FixedValuesEditor"

type Props = {
  expressionObj: {};
  onValueChange: Function;
  readOnly: boolean;
  className: string;
}

const SUPPORTED_LANGUAGE = "spel"
const SUPPORTED_FIELD_TYPE = "Boolean"
const TRUE_EXPRESSION = "true"
const FALSE_EXPRESSION = "false"

const parseable = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return (expression === "true" || expression === "false") && language === SUPPORTED_LANGUAGE
}

class BoolEditor extends React.Component<Props> {
  static switchableTo = (expressionObj) => parseable(expressionObj) || isEmpty(expressionObj.expression)
  static isSupported = (fieldType) => fieldType === SUPPORTED_FIELD_TYPE

  static get switchableToHint(): string {
    return i18next.t("editors.switchableToHint", "Switch to basic mode")
  }

  static get notSwitchableToHint(): string {
    return i18next.t("editors.notSwitchableToHint","Expression must be equal to true or false to switch to basic mode")
  }

  render(): ReactNode {
    const {expressionObj, readOnly, onValueChange, className} = this.props

    const trueValue = {expression: TRUE_EXPRESSION, label: i18next.t("common.true", "true")}
    const falseValue = {expression: FALSE_EXPRESSION, label: i18next.t("common.false", "false")}

    return (
        <ExpressionWithFixedValues
            values={[
              trueValue,
              falseValue,
            ]}
            defaultValue={trueValue}
            expressionObj={expressionObj}
            onValueChange={onValueChange}
            readOnly={readOnly}
            className={className}
        />
    )
  }
}

export default BoolEditor
