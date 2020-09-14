import React from "react"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import {errorValidator, mandatoryValueValidator, Validator, Error} from "./editors/Validators"
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

const Variable = (props: Props) => {

  const {node, onChange, isMarked, readOnly, showValidation, errors, variableTypes, renderFieldLabel, inferredVariableType} = props

  return (
    <div className="node-table-body node-variable-builder-body">
      <LabeledInput
        renderFieldLabel={() => renderFieldLabel("Name")}
        value={node.id}
        onChange={(event) => onChange("id", event.target.value)}
        isMarked={isMarked("id")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator]}
      />
      <LabeledInput
        renderFieldLabel={() => renderFieldLabel("Variable Name")}
        value={node.varName}
        onChange={(event) => onChange("varName", event.target.value)}
        isMarked={isMarked("varName")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[mandatoryValueValidator, errorValidator(errors, "varName")]}
      />
      <EditableEditor
        fieldName="expression"
        fieldLabel={"Expression"}
        renderFieldLabel={renderFieldLabel}
        expressionObj={node.value}
        onValueChange={((value) => onChange("value.expression", value))}
        readOnly={readOnly}
        showValidation={showValidation}
        showSwitch={false}
        errors={errors}
        variableTypes={variableTypes}
        validationLabelInfo={inferredVariableType}
      />
      <LabeledTextarea
        renderFieldLabel={() => renderFieldLabel("Description")}
        value={node?.additionalFields?.description || ""}
        onChange={(event) => onChange("additionalFields.description", event.target.value)}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
        className={"node-input"}
      />
    </div>
  )
}

Variable.defaultProps = {
  readOnly: false,
}

Variable.availableFields = ["id", "varName", DEFAULT_EXPRESSION_ID]

export default Variable
