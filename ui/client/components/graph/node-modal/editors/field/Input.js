import PropTypes from "prop-types"
import React from "react"
import {allValid} from "../Validators"
import ValidationLabels from "../../../../modals/ValidationLabels"
import classNames from "classnames"

export const Input = (props) => {
  const {
    isMarked, showValidation, className, placeholder, autoFocus, onChange, value, validators, readOnly,
    formattedValue, type, inputClassName
  } = props

  return (
    <div className={className}>
      <div className={isMarked ? " marked" : ""}>
        {
          <input
            autoFocus={autoFocus}
            type={type}
            readOnly={readOnly}
            placeholder={placeholder}
            className={classNames([
              !showValidation || allValid(validators, [formattedValue ? formattedValue : value]) ? "node-input" : "node-input node-input-with-error",
              inputClassName
            ])}
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
  formattedValue: PropTypes.string,
  className: PropTypes.string,
  type: PropTypes.string,
  inputClassName: PropTypes.string,
}

Input.defaultProps = {
  type: "text"
}

export default Input
