import cn from "classnames"
import React from "react"
import ExpressionSuggest from "./ExpressionSuggest"
import {Editor} from "./Editor"

type Props = {
  fieldName: string,
  expressionObj: $TodoType,
  validators: Array<$TodoType>,
  isMarked: boolean,
  showValidation: boolean,
  readOnly: boolean,
  onValueChange: Function,
  rows: number,
  cols: number,
  className: string,
  isRequiredParameter: boolean
}

const RawEditor = (props: Props) => {

  const {
    fieldName, expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows = 1, cols = 50, className, isRequiredParameter
  } = props

  return (
    <div className={className}>
      <ExpressionSuggest
        fieldName={fieldName}
        inputProps={{
          rows: rows,
          cols: cols,
          className: cn("node-input"),
          value: expressionObj.expression,
          language: expressionObj.language,
          onValueChange: onValueChange,
          readOnly: readOnly,
        }}
        validators={validators}
        isMarked={isMarked}
        showValidation={showValidation}
        isRequiredParameter
      />
    </div>
  )
}

export default RawEditor as Editor<Props>
