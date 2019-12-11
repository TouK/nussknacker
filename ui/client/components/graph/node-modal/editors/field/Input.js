import React from "react"
import {allValid} from "Common/Validators"
import ValidationLabels from "../../../../modals/ValidationLabels"
import PropTypes from "prop-types"

export const Input = (props) => {
  const {isMarked, showValidation, className, placeholder, autoFocus, onChange, value, validators, readOnly} = props

  return (
    <div className={className}>
      <div className={isMarked ? " marked" : ""}>
        {
          readOnly ?
            <div className="read-only" title={value}>{value}</div> :
            <input
              autoFocus={autoFocus}
              type="text"
              placeholder={placeholder}
              className={!showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"}
              value={value || ""}
              onChange={onChange}
            />
        }
      </div>
      {showValidation && <ValidationLabels validators={validators} values={[value]}/>}
    </div>
  )
}

Input.propTypes = {
  isMarked: PropTypes.bool,
  readOnly: PropTypes.bool,
  value: PropTypes.string,
  autoFocus: PropTypes.bool,
  placeholder: PropTypes.string,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  onChange: PropTypes.func
}


export default Input