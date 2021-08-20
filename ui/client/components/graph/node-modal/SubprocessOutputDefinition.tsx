import _ from "lodash"
import React from "react"
import {ExpressionLang} from "./editors/expression/types"
import {Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import Map from "./editors/map/Map"
import MapVariable from "./MapVariable"
import {NodeType, TypedObjectTypingResult, VariableTypes} from "../../../types";

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

const SubprocessOutputDefinition = (props: Props) => {

  const { onChange, renderFieldLabel, node, isMarked, readOnly, showValidation, errors, removeElement, variableTypes } = props

  const addField = () => {
    props.addElement("fields", {name: "", expression: {expression: "", language: ExpressionLang.SpEL}})
  }

  const onInputChange = (path, event) => props.onChange(path, event.target.value)

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
        variableTypes={variableTypes}
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

SubprocessOutputDefinition.defaultProps = MapVariable.defaultProps
SubprocessOutputDefinition.availableFields = (_node_) => {
  return ["id", "outputName"]
}

export default SubprocessOutputDefinition
