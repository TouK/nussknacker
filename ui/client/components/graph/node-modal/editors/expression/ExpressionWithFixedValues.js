import PropTypes from "prop-types"
import React from "react"
import Creatable from "react-select/creatable"
import {Types} from "./EditorType"

const getOptions = (values) => {
  return values.map((value) => ({
    value: value.expression,
    label: value.label,
  }))
}

export default class ExpressionWithFixedValues extends React.Component {

  constructor(props) {
    super(props)
    this.options = getOptions(props.values)
  }

  currentOption = (expressionObj, defaultValue) => {
    //TODO: is it ok to put not-existing option here?
    const defaultOption = {
      value: (expressionObj && expressionObj.expression) || (defaultValue && defaultValue.expression) || "",
      label: (expressionObj && expressionObj.expression) || (defaultValue && defaultValue.label) || "",
    }
    return this.options.find((option) => expressionObj && option.value === expressionObj.expression) || defaultOption
  }

  render() {
    const {
      expressionObj, readOnly, onValueChange, defaultValue, className,
    } = this.props
    const option = this.currentOption(expressionObj, defaultValue)

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

  static propTypes = {
    values: PropTypes.array,
    expressionObj: PropTypes.object,
    onValueChange: PropTypes.func,
    readOnly: PropTypes.bool,
    className: PropTypes.string,
  }
}

ExpressionWithFixedValues.isSupported = (fieldType) => fieldType === Types.EXPRESSION_WITH_FIXED_VALUES
