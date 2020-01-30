import React from "react"
import Select from "react-select"
import "../../stylesheets/processes.styl"

export default class TableSelect extends React.Component {

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
      backgroundColor: state.isSelected ? "#e6ecff" : null,
      color: "#555555",
    }),
  }

  customSelectTheme(theme) {
    return {
      ...theme,
      colors: {
        ...theme.colors,
        primary: "#0058a9",
      },
    }
  }

  render() {
    const {defaultValue, options, onChange, isMulti, isSearchable, placeholder} = this.props

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
          onChange={onChange}
          styles={this.customSelectStyles}
          theme={this.customSelectTheme}/>
      </div>
    )
  }
}
