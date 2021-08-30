import React from "react"
import {Expression, VariableTypes} from "../../../../../types"
import {NodeValue} from "../../subprocess-input-definition/NodeValue"
import EditableEditor from "../EditableEditor"
import {Error, Validator} from "../Validators"

interface MapValueProps {
  value: Expression,
  errors?: Array<Error>,
  variableTypes: VariableTypes,
  onChange?: (value: unknown) => void,
  validators?: Validator[],
  showValidation?: boolean,
  readOnly?: boolean,
  isMarked?: boolean,
  validationLabelInfo?: string,
}

export default function MapValue(props: MapValueProps): JSX.Element {
  const {value, isMarked, showValidation, readOnly, onChange, errors, variableTypes, validationLabelInfo} = props

  return (
    <NodeValue className="field">
      <EditableEditor
        errors={errors}
        isMarked={isMarked}
        readOnly={readOnly}
        showValidation={showValidation}
        onValueChange={(value) => onChange(value)}
        expressionObj={value}
        rowClassName={" "}
        valueClassName={" "}
        variableTypes={variableTypes}
        validationLabelInfo={validationLabelInfo}
      />
    </NodeValue>
  )
}
