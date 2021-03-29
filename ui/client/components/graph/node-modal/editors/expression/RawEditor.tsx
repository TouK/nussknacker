import cn from "classnames"
import React, {ForwardedRef, forwardRef} from "react"
import ReactAce from "react-ace/lib/ace"
import {UnknownFunction} from "../../../../../types/common"
import ExpressionSuggest from "./ExpressionSuggest"
import {Editor} from "./Editor"
import {VariableTypes} from "../../../../../types"
import {ExpressionObj} from "./types"

export type RawEditorProps = {
  expressionObj: ExpressionObj,
  validators: Array<$TodoType>,
  isMarked: boolean,
  showValidation: boolean,
  readOnly: boolean,
  onValueChange: UnknownFunction,
  rows?: number,
  cols?: number,
  className: string,
  variableTypes: VariableTypes,
  validationLabelInfo?: string,
}

const RawEditor = forwardRef(function RawEditor(props:RawEditorProps, forwardedRef: ForwardedRef<ReactAce>) {

  const {
    expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows = 1, cols = 50, className, variableTypes,
    validationLabelInfo,
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
          ref: forwardedRef,
        }}
        variableTypes={variableTypes}
        validators={validators}
        isMarked={isMarked}
        showValidation={showValidation}
        validationLabelInfo={validationLabelInfo}
      />
    </div>
  )
})

export default RawEditor
