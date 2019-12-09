import React from 'react'
import LabeledInput from "./LabeledInput"
import LabeledTextarea from "./LabeledTextarea"
import Checkbox from "./Checkbox"
import UnknownField from "./UnknownField"
import Input from "./Input"

const FieldTypes = {
  LabeledInput,
  LabeledTextarea,
  Checkbox,
  UnknownField,
  Input
}

const resolveComponentType = type => {
  switch (type) {
    case 'input':
      return LabeledInput.name
    case 'unlabeled-input':
      return Input.name
    case 'checkbox':
      return Checkbox.name
    case 'plain-textarea':
      return LabeledTextarea.name
    default:
      return UnknownField.name
  }
}

export const Field = (props) => {
  const {fieldType} = props
  const Component = FieldTypes[resolveComponentType(fieldType)]
  return (<Component {...props}/>)
}

export default Field