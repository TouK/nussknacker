import PropTypes from "prop-types"
import React from "react"
import {notEmptyValidator} from "../../../../../common/Validators";
import MapRow from "./MapRow";

const Map = (props) => {

  const {label, fields, onChange, addField, removeField, namespace, isMarked, readOnly, showValidation, expressionValue, errors} = props

  return (
    <div className="node-row">
      <div className="node-label" title={label}>{label}:</div>
      <div className="node-value">
        <div className="fieldsControl">
          {
            fields.map((field, index) => (<MapRow field={field}
                                                  validators={[notEmptyValidator]}
                                                  showValidation={showValidation}
                                                  readOnly={readOnly}
                                                  paths={`${namespace}[${index}]`}
                                                  isMarked={isMarked}
                                                  onChange={onChange}
                                                  onRemoveField={() => removeField(namespace, index)}/>))
          }
          {
            readOnly ? null :
              <div>
                <button onClick={addField}
                        className="addRemoveButton"
                        title="Add field">+
                </button>
              </div>
          }
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
  expressionValue: PropTypes.bool,
  showValidation: PropTypes.bool.isRequired
}

Map.defaultProps = {
  readOnly: false
}

export default Map