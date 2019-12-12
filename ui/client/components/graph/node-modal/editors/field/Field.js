import React from 'react'
import LabeledInput from "./LabeledInput"
import LabeledTextarea from "./LabeledTextarea"
import Checkbox from "./Checkbox"
import UnknownField from "./UnknownField"
import Input from "./Input"

export const Field = (props) => {
  const {fieldType} = props
  switch (fieldType) {
    case 'input':
      return (<LabeledInput {...props}/>)
    case 'unlabeled-input':
      return (<Input {...props}/>)
    case 'checkbox':
      return (<Checkbox {...props}/>)
    case 'plain-textarea':
      return (<LabeledTextarea {...props}/>)
    default:
      return (<UnknownField {...props}/>)
  }
}

export default Field