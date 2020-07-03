import cn from "classnames"
import React from "react"
import ExpressionSuggest from "./ExpressionSuggest"
import {Editor} from "./Editor"
import {VariableTypes} from "../../../../../types"

type Props = {
  expressionObj: $TodoType,
  validators: Array<$TodoType>,
  isMarked: boolean,
  showValidation: boolean,
  readOnly: boolean,
  onValueChange: Function,
  rows?: number,
  cols?: number,
  className: string,
  variableTypes: VariableTypes,

}

const RawEditor = (props: Props) => {

  const {
    expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows = 1, cols = 50, className, variableTypes,
  } = props

  return (
    <div className={className}>
      <ExpressionSuggest
        inputProps={{
          rows: rows,
          cols: cols,
          className: cn("node-input"),
          value: expressionObj.expression,
          language: expressionObj.language,
          onValueChange: onValueChange,
          readOnly: readOnly,
        }}
        variableTypes={variableTypes}
        validators={validators}
        isMarked={isMarked}
        showValidation={showValidation}
      />
    </div>
  )
}

export default RawEditor as Editor<Props>
