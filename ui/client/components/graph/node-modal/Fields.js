import PropTypes from "prop-types"
import React from "react"
import ExpressionSuggest from "../ExpressionSuggest"
import {allValid, notEmptyValidator} from "../../../common/Validators";
import ValidationLabels from "../../modals/ValidationLabels";

const Fields = (props) => {

  const {label, fields, onChange, addField, removeField, namespace, isMarked, readOnly, showValidation, expressionValue, errors} = props

  return (
    <div className="node-row">
      <div className="node-label" title={label}>{label}:</div>
      <div className="node-value">
        <div className="fieldsControl">
          {
            fields.map((field, index) => {
              const expression = field.expression
              const paths = `${namespace}[${index}]`
              const validators = [notEmptyValidator];
              return (
                <div className="node-row movable-row" key={field.uuid}>
                  <div className={"node-value fieldName" + (isMarked(paths) ? " marked" : "")}>
                    <input
                      className={!showValidation || allValid(validators, [field.name]) ? "node-input" : "node-input node-input-with-error"}
                      type="text"
                      value={field.name}
                      placeholder="Field name"
                      onChange={((e) => onChange(`${paths}.name`, e.target.value))}
                      readOnly={readOnly}
                    />
                    {showValidation && <ValidationLabels validators={validators} values={[field.name]}/>}
                  </div>
                  <div className={"node-value field"}>
                    <ExpressionSuggest
                      fieldName={`value-${field.uuid}`}
                      inputProps={{
                        onValueChange: ((value) => onChange(`${paths}.expression.expression`, value)),
                        value: expression.expression,
                        language: expression.language,
                        readOnly}}
                      validators={validators}
                      isMarked={isMarked(paths)}
                      showValidation={showValidation}
                    />
                  </div>
                  <div className={"node-value fieldRemove" + (isMarked(paths) ? " marked" : "")}>
                    <button
                      className="addRemoveButton"
                      title="Remove field"
                      onClick={() => removeField(namespace, index)}
                    >
                      -
                    </button>
                  </div>
                </div>)
            }
          )
          }
          <div>
            <button onClick={addField} className="addRemoveButton" title="Add field">+</button>
          </div>
        </div>
      </div>
    </div>
  )
}

Fields.propTypes = {
  fields: PropTypes.array.isRequired,
  label: PropTypes.string.isRequired,
  namespace: PropTypes.string.isRequired,
  isMarked: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  addField: PropTypes.func.isRequired,
  removeField: PropTypes.func.isRequired,
  readOnly: PropTypes.bool,
  expressionValue: PropTypes.bool,
  showValidation: PropTypes.bool.required
}

Fields.defaultProps = {
  readOnly: false
}

export default Fields