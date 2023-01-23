import cn from "classnames"
import React, {ForwardedRef, forwardRef, useMemo} from "react"
import ReactAce from "react-ace/lib/ace"
import ExpressionSuggest from "./ExpressionSuggest"
import {VariableTypes} from "../../../../../types"
import {ExpressionObj} from "./types"

export type RawEditorProps = {
  expressionObj: ExpressionObj,
  validators: Array<$TodoType>,
  isMarked: boolean,
  showValidation: boolean,
  readOnly: boolean,
  onValueChange: (value: string) => void,
  rows?: number,
  cols?: number,
  className: string,
  variableTypes: VariableTypes,
  validationLabelInfo?: string,
}

const RawEditor = forwardRef(function RawEditor(props: RawEditorProps, forwardedRef: ForwardedRef<ReactAce>) {

  const {
    expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows = 1, cols = 50, className, variableTypes,
    validationLabelInfo,
  } = props

  const value = useMemo(() => expressionObj.expression, [expressionObj.expression])
  const language = useMemo(() => expressionObj.language, [expressionObj.language])
  const className1 = useMemo(() => cn("node-input"), [])

  const inputProps = useMemo(() => ({
    rows: rows,
    cols: cols,
    className: className1,
    value: value,
    language: language,
    onValueChange: onValueChange,
    readOnly: readOnly,
    ref: forwardedRef,
  }), [rows, cols, className1, value, language, onValueChange, readOnly, forwardedRef])

  return (
    <div className={className}>
      <ExpressionSuggest
        inputProps={inputProps}
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
