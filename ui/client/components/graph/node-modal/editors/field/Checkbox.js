import React from "react"
import PropTypes from "prop-types"

export const Checkbox = (props) => {
  const {renderFieldLabel, autofocus, isMarked, value, onChange, readOnly} = props

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <div className={"node-value" + (isMarked ? " marked" : "")}>
        <input
          autoFocus={autofocus}
          type="checkbox"
          checked={value || false}
          onChange={(e) => onChange(e.target.checked)}
          disabled={readOnly ? 'disabled' : ''}
        />
      </div>
    </div>
  )
}

Checkbox.propTypes = {
  renderFieldLabel: PropTypes.func,
  autoFocus: PropTypes.bool,
  isMarked: PropTypes.bool,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool
}

export default Checkbox