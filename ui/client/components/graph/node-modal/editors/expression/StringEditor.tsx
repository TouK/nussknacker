import React from "react"
import Input from "../field/Input"
import {Editor} from "./Editor"

type Props = {
  expressionObj: $TodoType,
  onValueChange: Function,
  className: string,
  trim?: Function,
  format?: Function,
}

const StringEditor: Editor<Props> = (props: Props) => {

  const {expressionObj, onValueChange, className, trim, format} = props

  return (
    <Input
      {...props}
      onChange={(event) => onValueChange(format ? format(event.target.value) : event.target.value)}
      value={trim ? trim(expressionObj.expression) : expressionObj.expression}
      formattedValue={expressionObj.expression}
      className={className}
    />
  )
}

StringEditor.switchableTo = () => null
StringEditor.switchableToHint = () => null
StringEditor.notSwitchableToHint = () => null

export default StringEditor
