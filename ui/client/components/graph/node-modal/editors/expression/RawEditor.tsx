import React from "react"
import ExpressionSuggest from "./ExpressionSuggest"
import {$TodoType} from "../../../../../actions/migrationTypes";

type Props = {
  fieldName: string
  expressionObj: $TodoType
  validators: Array<$TodoType>
  isMarked: boolean
  showValidation: boolean
  readOnly: boolean
  onValueChange: Function
  rows: number
  cols: number
  className: string
}

export default function RawEditor(props: Props) {

  const {
    fieldName, expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows, cols, className,
  } = props

  return (
    <div className={className}>
      <ExpressionSuggest
        fieldName={fieldName}
        inputProps={{
          rows: rows,
          cols: cols,
          className: "node-input",
          value: expressionObj.expression,
          language: expressionObj.language,
          onValueChange: onValueChange,
          readOnly: readOnly,
        }}
        validators={validators}
        isMarked={isMarked}
        showValidation={showValidation}
      />
    </div>
  )
}

RawEditor.defaultProps = {
  rows: 1,
  cols: 50,
}
