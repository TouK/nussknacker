import update from "immutability-helper"
import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import {notEmptyValidator} from "../../../../common/Validators"
import MovableRow from "./MovableRow"

const FieldsSelect = (props) => {

  const {addField, fields, label, onChange, namespace, options, readOnly, removeField} = props

  const moveItem = (dragIndex, hoverIndex) => {
    const previousFields = _.cloneDeep(fields)
    const newFields = update(previousFields, {
      $splice: [[dragIndex, 1], [hoverIndex, 0, previousFields[dragIndex]]],
    })
    onChange(`${namespace}`, newFields)
  }

  const validators = [notEmptyValidator]

  return (
    <div className="node-row">
      <div className="node-label" title={label}>{label}:</div>
      <div className="node-value">
        <div className="fieldsControl">
          {
            fields.map((field, index) => {
              const paths = `${namespace}[${index}]`
              const currentOption = _.find(options, (item) => _.isEqual(field.typ.refClazzName, item.value)) || {label: field.typ.refClazzName, value: field.typ.refClazzName}
              return (
                <MovableRow
                  id={field.uuid}
                  key={field.uuid}
                  field={field}
                  index={index}
                  changeName={(name) => onChange(`${paths}.name`, name)}
                  changeValue={(value) => onChange(`${paths}.typ.refClazzName`, value)}
                  moveItem={moveItem}
                  remove={() => removeField(`${namespace}`, index)}
                  validators={validators}
                  value={currentOption}
                  {...props}
                />
              )
            })
          }
          {
            readOnly ? null :
              <div>
                <button className="addRemoveButton" title="Add field" onClick={() => addField()}>+</button>
              </div>
          }
        </div>
      </div>
    </div>
  )
}

FieldsSelect.propTypes = {
  addField: PropTypes.func.isRequired,
  fields: PropTypes.array.isRequired,
  isMarked: PropTypes.func.isRequired,
  label: PropTypes.string.isRequired,
  namespace: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  options: PropTypes.array.isRequired,
  readOnly: PropTypes.bool,
  removeField: PropTypes.func.isRequired,
  showValidation: PropTypes.bool.isRequired,
}

export default FieldsSelect
