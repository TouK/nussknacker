import PropTypes from "prop-types"
import React from "react"
import {ButtonWithFocus} from "../../../../withFocus"
import MapRow from "./MapRow"

const Map = (props) => {

  const {
    label, fields, onChange, addField, removeField, namespace, isMarked, readOnly, showValidation,
    showSwitch, errors, variableTypes,
  } = props

  return (
    <div className="node-row">
      <div className="node-label" title={label}>{label}:</div>
      <div className="node-value">
        <div className="fieldsControl">
          {
            fields.map((field, index) => (
              <MapRow
                key={field.uuid}
                field={field}
                showValidation={showValidation}
                showSwitch={showSwitch}
                readOnly={readOnly}
                paths={`${namespace}[${index}]`}
                isMarked={isMarked}
                onChange={onChange}
                onRemoveField={() => removeField(namespace, index)}
                errors={errors}
                variableTypes={variableTypes}
              />
            ))
          }
          {
            readOnly ? null : (
              <div>
                <ButtonWithFocus
                  onClick={addField}
                  className="addRemoveButton"
                  title="Add field"
                >+
                </ButtonWithFocus>
              </div>
            )}
        </div>
      </div>
    </div>
  )
}

Map.propTypes = {
  fields: PropTypes.array.isRequired,
  label: PropTypes.string.isRequired,
  namespace: PropTypes.string.isRequired,
  isMarked: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  addField: PropTypes.func.isRequired,
  removeField: PropTypes.func.isRequired,
  readOnly: PropTypes.bool,
  showValidation: PropTypes.bool.isRequired,
  showSwitch: PropTypes.bool,
  variableTypes: PropTypes.object.isRequired,
}

Map.defaultProps = {
  readOnly: false,
}

export default Map
