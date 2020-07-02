import PropTypes from "prop-types"
import React from "react"
import {mandatoryValueValidator} from "../Validators"
import MapKey from "./MapKey"
import MapValue from "./MapValue"

export default function MapRow(props) {
  const {field, showValidation, readOnly, paths, isMarked, onChange, onRemoveField, showSwitch, errors, variableTypes} = props

  return (
    <div className="node-row movable-row">
      <MapKey
        rowKey={field}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
        autofocus={false}
        isMarked={isMarked(paths)}
        readOnly={readOnly}
        paths={paths}
        onChange={onChange}
      />

      <MapValue
        rowKey={field}
        value={field.expression}
        isMarked={isMarked(paths)}
        paths={paths}
        errors={errors}
        showValidation={showValidation}
        showSwitch={showSwitch}
        readOnly={readOnly}
        onChange={onChange}
        variableTypes={variableTypes}
      />

      {
        readOnly ? null : (
          <div className={`node-value fieldRemove${  isMarked(paths) ? " marked" : ""}`}>
            <button
              className="addRemoveButton"
              title="Remove field"
              onClick={onRemoveField}
            >-
            </button>
          </div>
        )}
    </div>
  )
}

MapRow.propTypes = {
  field: PropTypes.object.isRequired,
  paths: PropTypes.string.isRequired,
  onChange: PropTypes.func,
  showSwitch: PropTypes.bool,
  variableTypes: PropTypes.object.isRequired,
}
