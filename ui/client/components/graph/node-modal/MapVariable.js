import PropTypes from "prop-types"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import {v4 as uuid4} from "uuid"
import React from "react"
import _ from "lodash"
import Map from "./editors/map/Map"
import {errorValidator, notEmptyValidator} from "../../../common/Validators"
import LabeledInput from "./editors/field/LabeledInput"

const MapVariable = (props) => {

  const {isMarked, node, removeElement, addElement, onChange, readOnly, showValidation, errors, renderFieldLabel} = props

  const addField = () => {
    addElement("fields", {"name": "", "uuid": uuid4(), "expression": {"expression": "", "language": "spel"}})
  }

  return (
    <div className="node-table-body node-variable-builder-body">
      <LabeledInput renderFieldLabel={() => renderFieldLabel("Id")}
                    value={node.id}
                    onChange={(event) => onChange("id", event.target.value)}
                    isMarked={isMarked("id")}
                    readOnly={readOnly}
                    showValidation={showValidation}
                    validators={[notEmptyValidator, errorValidator(errors, "id")]}/>

      <LabeledInput renderFieldLabel={() => renderFieldLabel("Variable Name")}
                    value={node.varName}
                    onChange={(event) => onChange("varName", event.target.value)}
                    isMarked={isMarked("varName")}
                    readOnly={readOnly}
                    showValidation={showValidation}
                    validators={[notEmptyValidator, errorValidator(errors, "varName")]}/>

      <Map label="Fields"
           onChange={onChange}
           fields={node.fields}
           removeField={removeElement}
           namespace="fields"
           addField={addField}
           isMarked={isMarked}
           readOnly={readOnly}
           showValidation={showValidation}/>

      <LabeledTextarea renderFieldLabel={() => renderFieldLabel("Description")}
                       value={_.get(props.node, "additionalFields.description", "")}
                       onChange={(event) => onChange("additionalFields.description", event.target.value)}
                       isMarked={isMarked("additionalFields.description")}
                       readOnly={readOnly}
                       className={"node-input"}/>
    </div>
  )
}

MapVariable.propTypes = {
  isMarked: PropTypes.func.isRequired,
  node: PropTypes.object.isRequired,
  removeElement: PropTypes.func.isRequired,
  addElement: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  readOnly: PropTypes.bool,
  showValidation: PropTypes.bool.isRequired
}

MapVariable.defaultProps = {
  readOnly: false
}

MapVariable.availableFields = (node) => {
  return ["id", "varName"]
}

export default MapVariable