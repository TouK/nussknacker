import React from "react"
import {allValid} from "../../../../../common/Validators"
import ValidationLabels from "../../../../modals/ValidationLabels"
import PropTypes from "prop-types"

export const Input = (props) => {
  const {isMarked, showValidation, className, placeholder, autoFocus, onChange, value, validators, readOnly} = props

  return (
    <div className={className}>
      <div className={isMarked ? " marked" : ""}>
        {
            <input
              autoFocus={autoFocus}
              type="text"
              readOnly={readOnly}
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
  value: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  readOnly: PropTypes.bool,
  autoFocus: PropTypes.bool,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  onChange: PropTypes.func,
  placeholder: PropTypes.string
}


export default Input