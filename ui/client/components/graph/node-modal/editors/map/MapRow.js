import PropTypes from "prop-types"
import React from "react"
import MapKey from "./MapKey"
import MapValue from "./MapValue"
import {notEmptyValidator} from "../../../../../common/Validators"

export default function MapRow(props) {
  const {field, validators, showValidation, readOnly, paths, isMarked, onChange, onRemoveField, showSwitch} = props

  return (
    <div className="node-row movable-row">
      <MapKey rowKey={field}
              showValidation={showValidation}
              validators={[notEmptyValidator]}
              autofocus={false}
              isMarked={isMarked(paths)}
              readOnly={readOnly}
              paths={paths}
              onChange={onChange}/>

      <MapValue rowKey={field}
                value={field.expression}
                isMarked={isMarked(paths)}
                paths={paths}
                validators={[notEmptyValidator]}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                onChange={onChange}/>

      {
        readOnly ? null :
          <div className={"node-value fieldRemove" + (isMarked(paths) ? " marked" : "")}>
            <button
              className="addRemoveButton"
              title="Remove field"
              onClick={onRemoveField}>-
            </button>
          </div>
      }
    </div>
  )
}

MapRow.propTypes = {
  field: PropTypes.object.isRequired,
  paths: PropTypes.string.isRequired,
  validators: PropTypes.array,
  onChange: PropTypes.func,
  showSwitch: PropTypes.bool
}