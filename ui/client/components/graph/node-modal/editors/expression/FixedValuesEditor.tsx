import React from "react"
import Creatable from "react-select/creatable"
import _ from "lodash";
import {$TodoType} from "../../../../../actions/migrationTypes";

type Props = {
  values?: $TodoType,
  expressionObj: $TodoType,
  onValueChange: Function,
  readOnly: boolean,
  className: string,
  defaultValue?: $TodoType,
  param?: $TodoType,
}

const getOptions = (values) => {
  return values.map((value) => ({
    value: value.expression,
    label: value.label,
  }))
}

export default class FixedValuesEditor extends React.Component<Props> {

  public static switchableTo = (expressionObj, values) => values.includes(expressionObj.expression)
  public static switchableToHint = "Switch to basic mode"
  public static notSwitchableToHint = "Expression must be one of the expression possible values to switch basic mode"

  private readonly options: any;

  constructor(props) {
    super(props)
    this.options = getOptions(props.values)
  }

  currentOption = () => {
    const {expressionObj, defaultValue, param} = this.props
    //TODO: is it ok to put not-existing option here?
    const defaultOption = {
      value: (_.get(_.head(_.get(param, "editor.possibleValues")), "expression")) || (expressionObj && expressionObj.expression) || (defaultValue && defaultValue.expression) || "",
      label: (_.get(_.head(_.get(param, "editor.possibleValues")), "label")) || (expressionObj && expressionObj.expression) || (defaultValue && defaultValue.label) || "",
    }
    return this.options.find((option) => expressionObj && option.value === expressionObj.expression) || defaultOption
  }

  render() {
    const {
      readOnly, onValueChange, className,
    } = this.props
    const option = this.currentOption()

    return (
      <Creatable
        className={`node-value-select ${className}`}
        classNamePrefix="node-value-select"
        value={option}
        onChange={(newValue) => onValueChange(newValue.value)}
        options={this.options}
        isDisabled={readOnly}
        formatCreateLabel={(x) => x}
      />
    )
  }
}
