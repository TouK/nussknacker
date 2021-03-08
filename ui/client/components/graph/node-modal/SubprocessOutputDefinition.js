import _ from "lodash"
import React from "react"
import {v4 as uuid4} from "uuid"
import {ExpressionLang} from "./editors/expression/types"
import {errorValidator, mandatoryValueValidator} from "./editors/Validators"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import Map from "./editors/map/Map"
import MapVariable from "./MapVariable"

const SubprocessOutputDefinition = ({isMarked, node, removeElement, addElement, onChange, readOnly, showValidation, errors, renderFieldLabel}) => {

  const addField = () => {
    addElement("fields", {name: "", uuid: uuid4(), expression: {expression: "", language: ExpressionLang.SpEL}})
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
        renderFieldLabel={() => renderFieldLabel("Output name")}
        value={node.outputName}
        onChange={(event) => onInputChange("outputName", event)}
        isMarked={isMarked("outputName")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator, errorValidator(errors, "outputName")]}
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

SubprocessOutputDefinition.propTypes = MapVariable.propTypes
SubprocessOutputDefinition.defaultProps = MapVariable.defaultProps
SubprocessOutputDefinition.availableFields = (_node_) => {
  return ["id", "outputName"]
}

export default SubprocessOutputDefinition
