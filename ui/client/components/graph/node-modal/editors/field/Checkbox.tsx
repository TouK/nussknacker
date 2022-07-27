import React from "react"
import {InputWithFocus} from "../../../../withFocus"
import {LabeledInputProps} from "./LabeledInput"

export interface CheckboxProps extends Pick<LabeledInputProps, "renderFieldLabel" | "autoFocus" | "isMarked" | "onChange" | "readOnly"> {
  value?: boolean,
  checked?: boolean,
}

export default function Checkbox(props: CheckboxProps): JSX.Element {
  const {renderFieldLabel, autoFocus, isMarked, value, onChange, readOnly} = props

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <div className={`node-value${isMarked ? " marked" : ""}${readOnly ? " read-only " : ""}`}>
        <InputWithFocus
          autoFocus={autoFocus}
          type="checkbox"
          checked={!!value}
          onChange={onChange}
          disabled={readOnly}
        />
      </div>
    </div>
  )
}
