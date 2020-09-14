import _ from "lodash"
import {errorValidator, mandatoryValueValidator, Error} from "./editors/Validators"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import Map from "./editors/map/Map"
import React from "react"
import {VariableTypes, NodeType, TypedObjectTypingResult, Field} from "../../../types"

type Props = {
  isMarked: (paths: string) => boolean,
  node: NodeType,
  removeElement: (namespace: string, ix: number) => void,
  addElement: (property: $TodoType, element: $TodoType) => void,
  onChange: (propToMutate: $TodoType, newValue: $TodoType, defaultValue?: $TodoType) => void,
  readOnly?: boolean,
  showValidation: boolean,
  errors: Array<Error>,
  variableTypes: VariableTypes,
  renderFieldLabel: (label: string) => React.ReactNode,
  expressionType?: TypedObjectTypingResult,
}

const MapVariable = (props: Props) => {

  const {
    isMarked, node, removeElement, addElement, onChange, readOnly, showValidation,
    errors, renderFieldLabel, variableTypes, expressionType,
  } = props

  const newField: Field = {name: "", expression: {expression: "", language: "spel"}}

  const addField = () => {
    addElement("fields", newField)
  }

  const onInputChange = (path: string, event: Event) => onChange(path, (event.target as HTMLInputElement).value)

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
        expressionType={expressionType}
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

MapVariable.defaultProps = {
  readOnly: false,
}

MapVariable.availableFields = (_node: NodeType) => {
  return ["id", "varName"]
}

export default MapVariable
