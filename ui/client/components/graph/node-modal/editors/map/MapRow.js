import PropTypes from "prop-types"
import React from "react"
import {ButtonWithFocus} from "../../../../withFocus"
import {mandatoryValueValidator} from "../Validators"
import MapKey from "./MapKey"
import MapValue from "./MapValue"

export default function MapRow(props) {
  const {field, showValidation, readOnly, paths, isMarked, onChange, onRemoveField, showSwitch, errors, variableTypes, validationLabelInfo} = props

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
        validationLabelInfo={validationLabelInfo}
      />

      {
        readOnly ? null : (
          <div className={`node-value fieldRemove${  isMarked(paths) ? " marked" : ""}`}>
            <ButtonWithFocus
              className="addRemoveButton"
              title="Remove field"
              onClick={onRemoveField}
            >-
            </ButtonWithFocus>
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
  validationLabelInfo: PropTypes.string,
  showValidation: PropTypes.bool.isRequired,
  readOnly: PropTypes.bool.isRequired,
  isMarked: PropTypes.func.isRequired,
  onRemoveField: PropTypes.func.isRequired,
  errors: PropTypes.array,
}
