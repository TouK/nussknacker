import {isEmpty} from "lodash"
import React from "react"
import ValidationLabels from "../../../../modals/ValidationLabels"
import {TextAreaWithFocus, TextAreaWithFocusProps} from "../../../../withFocus"
import {LabeledInputProps} from "./LabeledInput"

export interface LabeledTextareaProps extends Pick<LabeledInputProps, "value" | "isMarked" | "renderFieldLabel" | "showValidation" | "validators">,
  Pick<TextAreaWithFocusProps, "className" | "autoFocus" | "onChange" | "readOnly" | "cols" | "rows"> {
}

export default function LabeledTextarea(props: LabeledTextareaProps): JSX.Element {
  const {
    value,
    className,
    isMarked,
    rows = 1,
    cols = 50,
    renderFieldLabel,
    showValidation,
    validators,
    ...passProps
  } = props

  const lineEndPattern = /\r\n|\r|\n/

  return (
    <div className="node-row">
      {renderFieldLabel()}
      <div className={`node-value${isMarked ? " marked" : ""}`}>
        <TextAreaWithFocus
          {...passProps}
          rows={!isEmpty(value) ? value.split(lineEndPattern).length : rows}
          cols={cols}
          className={className}
          value={value}
        />
        {showValidation && <ValidationLabels validators={validators} values={[value]}/>}
      </div>
    </div>
  )
}
