import React, {PropsWithChildren} from "react"
import Checkbox from "./Checkbox"
import Input from "./Input"
import LabeledInput from "./LabeledInput"
import LabeledTextarea from "./LabeledTextarea"
import UnknownField from "./UnknownField"
import {Validator} from "../Validators"
import Select, {Option} from "./Select"

export enum FieldType {
  input = "input",
  unlabeledInput = "unlabeled-input",
  checkbox = "checkbox",
  plainTextarea = "plain-textarea",
  select = "select"
}

interface FieldProps {
  isMarked: boolean,
  readOnly: boolean,
  placeholder?: string,
  showValidation: boolean,
  autoFocus: boolean,
  className: string,
  validators: Validator[],
  type: FieldType,
  options?: Option[],
  value: string | boolean,
  onChange: (value: string | boolean | Option) => void,
}

function getCurrentBooleanOption(options: Option[], value: boolean | string): Option {
  return options.find(x => {
    const optionIsTrueFalse = x.value === value
    const optionIsNull = x.value === null && value === undefined
    return optionIsTrueFalse || optionIsNull
  })
}

export default function Field({type, children, ...props}: PropsWithChildren<FieldProps>): JSX.Element {
  switch (type) {
    case FieldType.input:
      return (
        <LabeledInput
          {...props}
          value={props.value?.toString() || ""}
          placeholder={props.placeholder}
          onChange={({target}) => props.onChange(target.value)}
        >
          {children}
        </LabeledInput>
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
        >
          {children}
        </Checkbox>
      )
    case FieldType.plainTextarea:
      return (
        <LabeledTextarea
          {...props}
          value={props.value?.toString() || ""}
          onChange={({target}) => props.onChange(target.value)}
        >
          {children}
        </LabeledTextarea>
      )
    case FieldType.select:
      return (
        <Select
          {...props}
          options={props.options}
          value={getCurrentBooleanOption(props.options, props.value) || null}
          onChange={(option) => {props.onChange(option.value)}}
        >
          {children}
        </Select>
      )
    default:
      return (<UnknownField/>)
  }
}
