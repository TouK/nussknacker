import classNames from "classnames"
import {allValid} from "../Validators"
import ValidationLabels from "../../../../modals/ValidationLabels"
import PropTypes from "prop-types"
import React from "react"
import {TextAreaWithFocus} from "../../../../withFocus"

export const Textarea = (props) => {
  const {
    isMarked, showValidation, className, placeholder, autoFocus, onChange, value, validators, readOnly,
    formattedValue, type, inputClassName, onFocus,
  } = props

  return (
    <div className={className}>
      <div className={isMarked ? " marked" : ""}>
        {
          <TextAreaWithFocus
            autoFocus={autoFocus}
            readOnly={readOnly}
            placeholder={placeholder}
            className={classNames([
              !showValidation || allValid(validators, [formattedValue ? formattedValue : value]) ? "node-input" : "node-input node-input-with-error",
              inputClassName,
            ])}
            value={value || ""}
            onChange={onChange}
            onFocus={onFocus}
          />
        }
      </div>
      {showValidation &&
      <ValidationLabels validators={validators} values={[formattedValue ? formattedValue : value]}/>}
    </div>
  )
}

Textarea.propTypes = {
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
  onFocus: PropTypes.func,
}

Textarea.defaultProps = { }

export default Textarea
