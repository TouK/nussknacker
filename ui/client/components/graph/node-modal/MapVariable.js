import _ from "lodash"
import PropTypes from "prop-types"
import {v4 as uuid4} from "uuid"
import {errorValidator, mandatoryValueValidator} from "./editors/Validators"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import Map from "./editors/map/Map"
import React from "react"

const MapVariable = ({isMarked, node, removeElement, addElement, onChange, readOnly, showValidation, errors, renderFieldLabel, variableTypes}) => {

  const addField = () => {
    addElement("fields", {name: "", uuid: uuid4(), expression: {expression: "", language: "spel"}})
  }

  const onInputChange = (path, event) => onChange(path, event.target.value)

  return (
    <div className="node-table-body node-variable-builder-body">
      <LabeledInput
        renderFieldLabel={() => renderFieldLabel("Name")}
        value={node.id}
        onChange={(event) => onInputChange("id", event)}
        isMarked={isMarked("id")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
      />

      <LabeledInput
        renderFieldLabel={() => renderFieldLabel("Variable Name")}
        value={node.varName}
        onChange={(event) => onInputChange("varName", event)}
        isMarked={isMarked("varName")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator, errorValidator(errors, "varName")]}
      />

      <Map
        label="Fields"
        onChange={onChange}
        fields={node.fields}
        removeField={removeElement}
        namespace="fields"
        addField={addField}
        isMarked={isMarked}
        readOnly={readOnly}
        showValidation={showValidation}
        variableTypes={variableTypes}
        showSwitch={false}
        errors={errors}
      />

      <LabeledTextarea
        renderFieldLabel={() => renderFieldLabel("Description")}
        value={_.get(node, "additionalFields.description", "")}
        onChange={(event) => onInputChange("additionalFields.description", event)}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
        className={"node-input"}
      />
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
  showValidation: PropTypes.bool.isRequired,
  errors: PropTypes.array.isRequired,
  variableTypes: PropTypes.object.isRequired,
  renderFieldLabel: PropTypes.func.isRequired,
}

MapVariable.defaultProps = {
  readOnly: false,
}

MapVariable.availableFields = (node) => {
  return ["id", "varName"]
}

export default MapVariable
