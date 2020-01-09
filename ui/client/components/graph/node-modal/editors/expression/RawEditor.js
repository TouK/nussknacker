import React from "react"
import ExpressionSuggest from "./ExpressionSuggest"
import PropTypes from "prop-types"

export default function RawEditor(props) {

  const {
    fieldName, expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows, cols, shouldShowSwitch, valueClassName
  } = props

  return (
      <div className={(shouldShowSwitch ? " switchable " : "") + valueClassName}>
        <ExpressionSuggest
          fieldName={fieldName}
          inputProps={{
            rows: rows,
            cols: cols,
            className: "node-input",
            value: expressionObj.expression,
            language: expressionObj.language,
            onValueChange: onValueChange,
            readOnly: readOnly
          }}
          validators={validators}
          isMarked={isMarked}
          showValidation={showValidation}
          shouldShowSwitch={shouldShowSwitch}
        />
      </div>
  )
}

RawEditor.propTypes = {
  valueClassName: PropTypes.string,
  fieldName: PropTypes.string,
  rows: PropTypes.number,
  cols: PropTypes.number,
  expressionObj: PropTypes.object,
  onValueChange: PropTypes.func,
  readOnly: PropTypes.bool,
  validators: PropTypes.array,
  isMarked: PropTypes.bool,
  showValidation: PropTypes.bool,
  shouldShowSwitch: PropTypes.bool
}

RawEditor.defaultProps = {
  rows: 1,
  cols: 50
}

RawEditor.supportedFieldTypes = ["String", "Boolean", "expression"]

RawEditor.switchableTo = (_) => true

RawEditor.switchableToHint = "Switch to expression mode"
