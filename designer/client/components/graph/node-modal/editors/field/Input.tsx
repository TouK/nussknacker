import React from "react"
import ValidationLabels from "../../../../modals/ValidationLabels"
import {InputWithFocus, InputWithFocusProps} from "../../../../withFocus"
import {allValid, Validator} from "../Validators"
import {cx} from "@emotion/css"

export interface InputProps extends Pick<InputWithFocusProps, "className" | "placeholder" | "autoFocus" | "onChange" | "readOnly" | "type" | "onFocus" | "disabled"> {
  value: string,
  formattedValue?: string,
  inputClassName?: string,
  validators?: Validator[],
  isMarked?: boolean,
  showValidation?: boolean,
}

export default function Input(props: InputProps): JSX.Element {
  const {
    isMarked, showValidation, className, value, validators,
    formattedValue, type = "text", inputClassName,
    autoFocus, readOnly, placeholder, onFocus, onChange,
  } = props

  return (
    <div className={className}>
      <div className={isMarked ? " marked" : ""}>
        {
          <InputWithFocus
            autoFocus={autoFocus}
            readOnly={readOnly}
            placeholder={placeholder}
            onChange={onChange}
            onFocus={onFocus}
            type={type}
            className={cx([
              !showValidation || allValid(validators, [formattedValue ? formattedValue : value]) ? "node-input" : "node-input node-input-with-error",
              inputClassName,
            ])}
            value={value || ""}
          />
        }
      </div>
      {showValidation && <ValidationLabels validators={validators} values={[formattedValue ? formattedValue : value]}/>}
    </div>
  )
}
