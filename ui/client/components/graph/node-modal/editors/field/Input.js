import PropTypes from "prop-types"
import React from "react"
import {allValid} from "../../../../../common/Validators"
import ValidationLabels from "../../../../modals/ValidationLabels"

export const Input = (props) => {
  const {isMarked, showValidation, className, placeholder, autoFocus, onChange, value, validators, readOnly, formattedValue} = props

  return (
    <div className={className}>
      <div className={isMarked ? " marked" : ""}>
        {
            <input
              autoFocus={autoFocus}
              type="text"
              readOnly={readOnly}
              placeholder={placeholder}
              className={!showValidation || allValid(validators, [formattedValue ? formattedValue : value]) ? "node-input" : "node-input node-input-with-error"}
              value={value || ""}
              onChange={onChange}
            />
        }
      </div>
      {showValidation && <ValidationLabels validators={validators} values={[formattedValue ? formattedValue : value]}/>}
    </div>
  )
}

Input.propTypes = {
  isMarked: PropTypes.bool,
  value: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number,
  ]),
  readOnly: PropTypes.bool,
  autoFocus: PropTypes.bool,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
}

export default Input
