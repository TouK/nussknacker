import PropTypes from "prop-types"
import React from "react"
import Input from "../field/Input"

export default function MapKey(props) {
  const {rowKey, autofocus, isMarked, paths, showValidation, validators, readOnly, onChange} = props

  return (
    <div className={`node-value fieldName${  isMarked ? " marked" : ""}`}>
      <Input isMarked={isMarked}
             readOnly={readOnly}
             value={rowKey.name}
             placeholder="Field name"
             autofocus={autofocus}
             showValidation={showValidation}
             validators={validators}
             onChange={(e) => onChange(`${paths}.name`, e.target.value)}/>
    </div>
  )
}

MapKey.propTypes = {
  rowKey: PropTypes.object.isRequired,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  readOnly: PropTypes.bool,
  onChange: PropTypes.func,
}
