import _ from "lodash"
import React from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../../common/ProcessUtils"
import {Error, mandatoryValueValidator} from "../editors/Validators"
import LabeledInput from "../editors/field/LabeledInput"
import LabeledTextarea from "../editors/field/LabeledTextarea"
import FieldsSelect from "./FieldsSelect"
import {NodeType, TypedObjectTypingResult, VariableTypes} from "../../../../types";
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"

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
  toogleCloseOnEsc: () => void,
}

export default function SubprocessInputDefinition(props: Props): JSX.Element {

  const {addElement, isMarked, node, onChange, readOnly, removeElement, showValidation, renderFieldLabel} = props


  const typeOptions = (useSelector(getProcessDefinitionData)?.processDefinition?.typesInformation || []).map(type => ({
    value: type.clazzName.refClazzName,
    label: ProcessUtils.humanReadableType(type.clazzName),
  }))
  const orderedTypeOptions = _.orderBy(typeOptions, (item) => [item.label, item.value], ["asc"])

  const defaultTypeOption = _.find(typeOptions, {label: "String"}) || _.head(typeOptions)

  const onInputChange = (path, event) => onChange(path, event.target.value)

  const addField = () => {
    addElement("parameters", {name: "", typ: {refClazzName: defaultTypeOption.value}})
  }

  return (
    <div className="node-table-body">
      <LabeledInput
        renderFieldLabel={() => renderFieldLabel("Name")}
        value={node.id}
        onChange={(event) => onInputChange("id", event)}
        isMarked={isMarked("id")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
      />

      <FieldsSelect
        label="Parameters"
        onChange={onChange}
        addField={addField}
        removeField={removeElement}
        namespace={"parameters"}
        fields={node.parameters || []}
        options={orderedTypeOptions}
        isMarked={index => isMarked(`parameters[${index}].name`) || isMarked(`parameters[${index}].typ.refClazzName`)}
        toogleCloseOnEsc={props.toogleCloseOnEsc}
        showValidation={showValidation}
        readOnly={readOnly}
      />

      <LabeledTextarea
        renderFieldLabel={() => renderFieldLabel("Description")}
        value={_.get(node, "additionalFields.description", "")}
        className={"node-input"}
        onChange={(event) => onInputChange("additionalFields.description", event)}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
      />

      {/*placeholder for Select drop-down menu*/}
      <div className="drop-down-menu-placeholder"/>
    </div>
  )
}