import React from "react"
import Creatable from "react-select/creatable"
import PropTypes from 'prop-types'
import SwitchIcon from "./SwitchIcon"

const getOptions = (values) => {
  return values.map((value) => ({
    value: value.expression,
    label: value.label
  }))
}

export default class ExpressionWithFixedValues extends React.Component {
  constructor(props) {
    super(props)
    this.options = getOptions(props.values)
  }

  currentOption = (expressionObj, defaultValue) => {
    const defaultOption = {
      value: expressionObj.expression || defaultValue.expression || "",
      label: expressionObj.expression || defaultValue.expression || ""
    }
    return this.options.find((option) => option.value === expressionObj.expression) || defaultOption
  }

  render() {
    const {
      expressionObj, fieldLabel, readOnly, toggleEditor, shouldShowSwitch, switchable, rowClassName, valueClassName,
      renderFieldLabel, onValueChange, displayRawEditor, defaultValue
    } = this.props
    const option = this.currentOption(expressionObj, defaultValue)

    return (
      <div className={rowClassName ? rowClassName : " node-row"}>
        {fieldLabel && renderFieldLabel(fieldLabel)}
        <Creatable
          className={"node-value-select node-value " + (shouldShowSwitch ? " switchable" : "")}
          classNamePrefix="node-value-select"
          value={option}
          onChange={(newValue) => onValueChange(newValue.value)}
          options={this.options}
          isDisabled={readOnly}
          formatCreateLabel={(x) => x}
        />
        <SwitchIcon
          switchable={switchable}
          onClick={toggleEditor}
          shouldShowSwitch={shouldShowSwitch}
          displayRawEditor={displayRawEditor}
          readOnly={readOnly}
        />
      </div>
    )
  }

  static propTypes = {
    values: PropTypes.array,
    renderFieldLabel: PropTypes.func,
    expressionObj: PropTypes.object,
    fieldLabel: PropTypes.string,
    onValueChange: PropTypes.func,
    readOnly: PropTypes.bool,
    shouldShowSwitch: PropTypes.bool,
    rowClassName: PropTypes.string,
    valueClassName: PropTypes.string
  }
}