import React from "react";
import PropTypes from 'prop-types';
import ValidationLabels from "../../../../modals/ValidationLabels";
import {allValid} from "../../../../../common/Validators";

export default function MapKey(props) {
  const {rowKey, isMarked, paths, showValidation, validators, readOnly, onChange} = props

  return (
    <div className={"node-value fieldName" + (isMarked ? " marked" : "")}>
      <input
        className={!showValidation || allValid(validators, [rowKey.name]) ? "node-input" : "node-input node-input-with-error"}
        type="text"
        value={rowKey.name}
        placeholder="Field name"
        onChange={((e) => onChange(`${paths}.name`, e.target.value))}
        readOnly={readOnly}
      />
      {showValidation && <ValidationLabels validators={validators} values={[rowKey.name]}/>}
    </div>
  )
}

MapKey.propTypes = {
  rowKey: PropTypes.object.isRequired,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  readOnly: PropTypes.bool,
  onChange: PropTypes.func
}