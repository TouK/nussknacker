import React from "react"
import PropTypes from "prop-types"
import Input from "./Input"

export const LabeledInput = (props) => {
  const {renderFieldLabel, placeholder, isMarked, readOnly, value, autofocus, showValidation, validators, onChange} = props

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <Input isMarked={isMarked}
             readOnly={readOnly}
             value={value}
             className={"node-value"}
             autoFocus={autofocus}
             placeholder={placeholder}
             showValidation={showValidation}
             validators={validators}
             onChange={(e) => onChange(e.target.value)}/>
    </div>
  )
}

LabeledInput.propTypes = {
  renderFieldLabel: PropTypes.func.isRequired,
  placeholder: PropTypes.string,
  isMarked: PropTypes.bool,
  readOnly: PropTypes.bool,
  autofocus: PropTypes.bool,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  value: PropTypes.string,
  onChange: PropTypes.func,
}

export default LabeledInput