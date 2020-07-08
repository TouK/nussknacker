import React from "react"
import Creatable from "react-select/creatable"
import {ExpressionObj} from "./types"
import ValidationLabels from "../../../../modals/ValidationLabels"
import {Validator} from "../Validators"

type Props = {
  editorConfig: $TodoType,
  expressionObj: $TodoType,
  onValueChange: Function,
  readOnly: boolean,
  className: string,
  param?: $TodoType,
  showValidation: boolean,
  validators: Array<Validator>,
}

const getOptions = (values) => {
  return values.map((value) => ({
    value: value.expression,
    label: value.label,
  }))
}

export default class FixedValuesEditor extends React.Component<Props> {

  public static switchableTo = (expressionObj: ExpressionObj, editorConfig) => editorConfig.possibleValues.map(v => v.expression).includes(expressionObj.expression)
  public static switchableToHint = () => "Switch to basic mode"
  public static notSwitchableToHint = () => "Expression must be one of the expression possible values to switch basic mode"

  currentOption = (expressionObj, options) => {
    return expressionObj && options.find((option) => option.value === expressionObj.expression) ||  // current value with label taken from options
        expressionObj && {value: expressionObj.expression, label: expressionObj.expression} ||          // current value is no longer valid option? Show it anyway, let user know. Validation should take care
        null                                                                                            // just leave undefined and let the user explicitly select one
  }

  render() {
    const {
      expressionObj, readOnly, onValueChange, className, showValidation, validators, editorConfig,
    } = this.props
    const options = getOptions(editorConfig.possibleValues)
    const currentOption = this.currentOption(expressionObj, options)

    return (
      <div className={`node-value-select ${className}`}>
        <Creatable
          classNamePrefix="node-value-select"
          value={currentOption}
          onChange={(newValue) => onValueChange(newValue.value)}
          options={options}
          isDisabled={readOnly}
          formatCreateLabel={(x) => x}
        />
        {showValidation && <ValidationLabels validators={validators} values={[currentOption.value]}/>}
      </div>
    )
  }
}
