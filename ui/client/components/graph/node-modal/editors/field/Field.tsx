import React from "react"
import Checkbox from "./Checkbox"
import Input from "./Input"
import LabeledInput from "./LabeledInput"
import LabeledTextarea from "./LabeledTextarea"
import UnknownField from "./UnknownField"
import {Validator} from "../Validators"

export enum FieldType {
  input = "input",
  unlabeledInput = "unlabeled-input",
  checkbox = "checkbox",
  plainTextarea = "plain-textarea",
}

interface FieldProps {
  isMarked: boolean,
  readOnly: boolean,
  showValidation: boolean,
  autoFocus: boolean,
  className: string,
  renderFieldLabel: () => JSX.Element,
  validators: Validator[],
  type: FieldType,
  value: string | boolean,
  onChange: (value: string | boolean) => void,
}

export default function Field({type, ...props}: FieldProps): JSX.Element {
  switch (type) {
    case FieldType.input:
      return (
        <LabeledInput
          {...props}
          value={props.value?.toString() || ""}
          onChange={({target}) => props.onChange(target.value)}
        />
      )
    case FieldType.unlabeledInput:
      return (
        <Input
          {...props}
          value={props.value?.toString() || ""}
          onChange={({target}) => props.onChange(target.value)}
        />
      )
    case FieldType.checkbox:
      return (
        <Checkbox
          {...props}
          value={!!props.value}
          onChange={({target}) => props.onChange(target.checked)}
        />
      )
    case FieldType.plainTextarea:
      return (
        <LabeledTextarea
          {...props}
          value={props.value?.toString() || ""}
          onChange={({target}) => props.onChange(target.value)}
        />
      )
    default:
      return (<UnknownField/>)
  }
}
