import React from "react"
import ExpressionSuggest from "./ExpressionSuggest"
import PropTypes from "prop-types"
import SwitchIcon from "./SwitchIcon"

export default function RawEditor(props) {

  const {
    renderFieldLabel, fieldLabel, fieldName, expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows, cols, switchable, toggleEditor, shouldShowSwitch, rowClassName, valueClassName
  } = props

  return (
    <div className={rowClassName}>
      {fieldLabel && renderFieldLabel(fieldLabel)}
      <div className={valueClassName}>
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
        />
      </div>
      <SwitchIcon switchable={switchable} onClick={toggleEditor} shouldShowSwitch={shouldShowSwitch}/>
    </div>
  )
}

RawEditor.propTypes = {
  fieldLabel: PropTypes.string,
  renderFieldLabel: PropTypes.func,
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
  switchable: PropTypes.bool,
  toggleEditor: PropTypes.func,
  shouldShowSwitch: PropTypes.bool
}

RawEditor.defaultProps = {
  rows: 1,
  cols: 50
}
