import React from "react";
import Creatable from "react-select/lib/Creatable";
import PropTypes from 'prop-types';

const getOptions = (values) => {
  return values.map((value) => ({
    value: value.expression,
    label: value.label
  }))
};

export default class ExpressionWithFixedValues extends React.Component {
  static propTypes = {
    values: PropTypes.array.isRequired,
    renderFieldLabel: PropTypes.func.isRequired,
    obj: PropTypes.object.isRequired,
    fieldLabel: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired,
    readOnly: PropTypes.bool.isRequired
  };

  constructor(props) {
    super(props);
    this.options = getOptions(props.values);
  }

  currentOption = (obj) => {
    const defaultOption = {value: obj.expression || "", label: obj.expression || ""};
    return this.options.find((option) => option.value === obj.expression) || defaultOption;
  };

  handleChange = (newValue) => {
    this.props.onChange(newValue.value);
  };

  render() {
    const {obj, fieldLabel, readOnly} = this.props;
    const option = this.currentOption(obj);

    return (
      <div className="node-row">
        {this.props.renderFieldLabel(fieldLabel)}
        <Creatable
          className="node-value node-value-select"
          classNamePrefix="node-value-select"
          value={option}
          onChange={this.handleChange}
          options={this.options}
          isDisabled={readOnly}
          formatCreateLabel={(x) => x}
        />
      </div>
    );
  }
}