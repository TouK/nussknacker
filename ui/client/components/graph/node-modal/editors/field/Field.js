import React from "react"
import LabeledInput from "./LabeledInput"
import LabeledTextarea from "./LabeledTextarea"
import Checkbox from "./Checkbox"
import UnknownField from "./UnknownField"
import Input from "./Input"

export const Field = (props) => {
  const {fieldType, onChange} = props
  switch (fieldType) {
    case "input":
      return (<LabeledInput {...props} onChange={(e) => onChange(e.target.value)}/>)
    case "unlabeled-input":
      return (<Input {...props} onChange={(e) => onChange(e.target.value)}/>)
    case "checkbox":
      return (<Checkbox {...props} onChange={(e) => onChange(e.target.checked)}/>)
    case "plain-textarea":
      return (<LabeledTextarea {...props} onChange={(e) => onChange(e.target.value)}/>)
    default:
      return (<UnknownField {...props}/>)
  }
}

export default Field