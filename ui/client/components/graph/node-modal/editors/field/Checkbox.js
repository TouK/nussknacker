import PropTypes from "prop-types"
import React from "react"
import {InputWithFocus} from "../../../../withFocus"

export const Checkbox = (props) => {
  const {renderFieldLabel, autofocus, isMarked, value, onChange, readOnly} = props

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <div className={`node-value${  isMarked ? " marked" : ""}${readOnly ? " read-only " : ""}`}>
        <InputWithFocus
          autoFocus={autofocus}
          type="checkbox"
          checked={value || false}
          onChange={onChange}
          disabled={readOnly ? "disabled" : ""}
        />
      </div>
    </div>
  )
}

Checkbox.propTypes = {
  renderFieldLabel: PropTypes.func,
  isMarked: PropTypes.bool,
  readOnly: PropTypes.bool,
  autoFocus: PropTypes.bool,
  onChange: PropTypes.func,
}

export default Checkbox
