import React from "react"
import {NodeValue} from "../../subprocess-input-definition/NodeValue"
import Input from "../field/Input"
import {Validator} from "../Validators"

interface MapKeyProps {
  value: string,
  onChange?: (value: string) => void,
  isMarked?: boolean,
  readOnly?: boolean,
  showValidation?: boolean,
  autofocus?: boolean,
  validators?: Validator[],
}

export default function MapKey(props: MapKeyProps): JSX.Element {
  const {value, autofocus, isMarked, showValidation, validators, readOnly, onChange} = props
  return (
    <NodeValue className="fieldName">
      <Input
        isMarked={isMarked}
        readOnly={readOnly}
        value={value}
        placeholder="Field name"
        autofocus={autofocus}
        showValidation={showValidation}
        validators={validators}
        onChange={(e) => onChange(e.target.value)}
      />
    </NodeValue>
  )
}
