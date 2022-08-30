import React, {PropsWithChildren} from "react"
import Input, {InputProps} from "./Input"

export type LabeledInputProps = PropsWithChildren<Pick<InputProps, "placeholder" | "isMarked" | "readOnly" | "value" | "autoFocus" | "showValidation" | "validators" | "onChange">>

export default function LabeledInput({children, ...props}: LabeledInputProps): JSX.Element {
  return (
    <div className="node-row">
      {children}
      <Input
        {...props}
        className={"node-value"}
      />
    </div>
  )
}
