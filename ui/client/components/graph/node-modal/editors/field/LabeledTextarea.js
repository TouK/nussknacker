import PropTypes from "prop-types"
import React from "react"
import ValidationLabels from "../../../../modals/ValidationLabels"

export const LabeledTextarea = (props) => {
  const {autoFocus, value, onChange, className, isMarked, readOnly, rows, cols, renderFieldLabel, showValidation, validators} = props

  const lineEndPattern = /\r\n|\r|\n/

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <div className={`node-value${  isMarked ? " marked" : ""}`}>
        <textarea
          rows={!_.isEmpty(value) ? value.split(lineEndPattern).length : rows}
          cols={cols}
          className={className}
          value={value}
          onChange={onChange}
          readOnly={readOnly}
          autoFocus={autoFocus}/>
        {showValidation && <ValidationLabels validators={validators} values={[value]}/>}
      </div>
    </div>
  )
}

LabeledTextarea.propTypes = {
  renderFieldLabel: PropTypes.func.isRequired,
  isMarked: PropTypes.bool,
  readOnly: PropTypes.bool,
  value: PropTypes.string.isRequired,
  autoFocus: PropTypes.bool,
  showValidation: PropTypes.bool,
  onChange: PropTypes.func.isRequired,
  className: PropTypes.string,
  rows: PropTypes.number,
  cols: PropTypes.number,
}

LabeledTextarea.defaultProps = {
  isMarked: false,
  readOnly: false,
  autoFocus: false,
  rows: 1,
  cols: 50,
}

export default LabeledTextarea
