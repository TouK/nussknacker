import PropTypes from "prop-types"
import React from "react"
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
             onChange={onChange}/>
    </div>
  )
}

LabeledInput.propTypes = {
  renderFieldLabel: PropTypes.func.isRequired,
  isMarked: PropTypes.bool,
  readOnly: PropTypes.bool,
  value: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number,
  ]),
  autofocus: PropTypes.bool,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
}

export default LabeledInput
