import React from "react"
import Select from "react-select"
import "../../stylesheets/processes.styl"

type Props = {
  defaultValue: $TodoType,
  onChange: $TodoType,
  options: $TodoType[],
  isMulti: boolean,
  isSearchable: boolean,
  placeholder: string,
}

export default class TableSelect extends React.Component<Props> {

  customSelectStyles = {
    control: styles => ({
      ...styles,
      minHeight: 45,
      fontSize: 14,
      color: "#555555",
      borderRadius: 0,
    }),
    option: (styles, state) => ({
      ...styles,
      fontSize: 14,
      backgroundColor: state.isSelected ? "#E6ECFF" : null,
      color: "#555555",
    }),
  }

  customSelectTheme(theme) {
    return {
      ...theme,
      colors: {
        ...theme.colors,
        primary: "#0058A9",
      },
    }
  }

  render() {
    const {defaultValue, options, isMulti, isSearchable, placeholder} = this.props

    return (
      <div id="table-filter" className="input-group">
        <Select
          isMulti={isMulti}
          isSearchable={isSearchable}
          defaultValue={defaultValue}
          closeMenuOnSelect={false}
          className="form-select"
          options={options}
          placeholder={placeholder}
          onChange={this.getOnChange}
          styles={this.customSelectStyles}
          theme={this.customSelectTheme}
        />
      </div>
    )
  }

  getOnChange = value => {
    const {onChange, isMulti} = this.props
    onChange(isMulti ? value || [] : value)
  }
}
