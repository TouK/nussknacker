import React from "react"
import Input, {InputProps} from "./Input"

export interface LabeledInputProps extends Pick<InputProps, "placeholder" | "isMarked" | "readOnly" | "value" | "autoFocus" | "showValidation" | "validators" | "onChange"> {
  renderFieldLabel: () => React.ReactNode,
}

export default function LabeledInput({renderFieldLabel, ...props}: LabeledInputProps): JSX.Element {
  return (
    <div className="node-row">
      {renderFieldLabel()}
      <Input
        {...props}
        className={"node-value"}
      />
    </div>
  )
}
