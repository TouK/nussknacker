import React, {useCallback} from "react"
import {Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"
import EditableEditor from "./editors/EditableEditor"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"
import {NodeType, VariableTypes} from "../../../types"

type Props = {
  readOnly?: boolean,
  isMarked: (fieldName: string) => boolean,
  node: NodeType,
  onChange: (fieldName: string, value: string) => void,
  showValidation: boolean,
  showSwitch?: boolean,
  variableTypes: VariableTypes,
  inferredVariableType?: string,
  renderFieldLabel: (label: string) => React.ReactNode,
  errors?: Error[],
}

export default function Variable(props: Props): JSX.Element {
  const {
    node,
    onChange,
    isMarked,
    readOnly,
    showValidation,
    errors,
    variableTypes,
    renderFieldLabel,
    inferredVariableType,
  } = props

  const onExpressionChange = useCallback((value: string) => onChange("value.expression", value), [onChange])

  return (
    <div className="node-table-body node-variable-builder-body">
      <LabeledInput
        value={node.id}
        onChange={(event) => onChange("id", event.target.value)}
        isMarked={isMarked("id")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
      >
        {renderFieldLabel("Name")}
      </LabeledInput>
      <LabeledInput
        value={node.varName}
        onChange={(event) => onChange("varName", event.target.value)}
        isMarked={isMarked("varName")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator, errorValidator(errors, "varName")]}
      >
        {renderFieldLabel("Variable Name")}
      </LabeledInput>
      <EditableEditor
        fieldName="expression"
        fieldLabel={"Expression"}
        renderFieldLabel={renderFieldLabel}
        expressionObj={node.value}
        onValueChange={onExpressionChange}
        readOnly={readOnly}
        showValidation={showValidation}
        showSwitch={false}
        errors={errors}
        variableTypes={variableTypes}
        validationLabelInfo={inferredVariableType}
      />
      <LabeledTextarea
        value={node?.additionalFields?.description || ""}
        onChange={(event) => onChange("additionalFields.description", event.target.value)}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
        className={"node-input"}
      >
        {renderFieldLabel("Description")}
      </LabeledTextarea>
    </div>
  )
}
