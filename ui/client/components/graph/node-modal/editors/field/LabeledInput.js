import React from "react"
import PropTypes from "prop-types"
import Input from "./Input"
import SwitchIcon from "../expression/SwitchIcon"

export const LabeledInput = (props) => {
  const {
    renderFieldLabel, placeholder, isMarked, readOnly, value, autofocus, showValidation, validators, onChange,
    shouldShowSwitch, switchable, toggleEditor, displayRawEditor, fieldType
  } = props

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <Input isMarked={isMarked}
             readOnly={readOnly}
             value={value}
             className={(shouldShowSwitch ? "switchable " : "") + "node-value"}
             autoFocus={autofocus}
             placeholder={placeholder}
             showValidation={showValidation}
             validators={validators}
             onChange={onChange}/>
      <SwitchIcon
        switchable={switchable}
        onClick={toggleEditor}
        shouldShowSwitch={shouldShowSwitch}
        displayRawEditor={displayRawEditor}
        readOnly={readOnly}
        fieldType={fieldType}
      />
    </div>
  )
}

LabeledInput.propTypes = {
  renderFieldLabel: PropTypes.func.isRequired,
  isMarked: PropTypes.bool,
  readOnly: PropTypes.bool,
  value: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  autofocus: PropTypes.bool,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  onChange: PropTypes.func,
  placeholder: PropTypes.string
}

export default LabeledInput