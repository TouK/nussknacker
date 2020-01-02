import React from "react"
import ExpressionSuggest from "./ExpressionSuggest"
import PropTypes from "prop-types"
import SwitchIcon from "./SwitchIcon"
import {Types} from "./EditorType"
import {nonSwitchableToBoolEditorHint, switchableToBoolEditor} from "./BoolEditor"
import {nonSwitchableToStringEditorHint, switchableToStringEditor} from "./StringEditor"

export default function RawEditor(props) {

  const {
    renderFieldLabel, fieldLabel, fieldName, expressionObj, validators, isMarked, showValidation, readOnly,
    onValueChange, rows, cols, switchable, toggleEditor, shouldShowSwitch, rowClassName, valueClassName, displayRawEditor,
    fieldType, editorName
  } = props

  const switchableToBasicEditor = (editorName, expressionObj, fieldType) =>
    (fieldType === Types.BOOLEAN && switchableToBoolEditor(expressionObj))
    || (fieldType === Types.EXPRESSION && switchableToBoolEditor(expressionObj))
    || (fieldType === Types.STRING && switchableToStringEditor(expressionObj))

  const switchToBasicEditorHint = () => {
    if (switchableToBasicEditor(editorName, expressionObj, fieldType)) {
      return "Switch to basic mode"
    } else if (fieldType === Types.BOOLEAN || fieldType === Types.EXPRESSION) {
      return nonSwitchableToBoolEditorHint
    } else if (fieldType === Types.STRING) {
      return nonSwitchableToStringEditorHint
    }
  }

  return (
    <div className={rowClassName}>
      {fieldLabel && renderFieldLabel(fieldLabel)}
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
      <SwitchIcon
        switchable={switchableToBasicEditor(editorName, expressionObj, fieldType)}
        hint={switchToBasicEditorHint()}
        onClick={toggleEditor}
        shouldShowSwitch={shouldShowSwitch}
        displayRawEditor={displayRawEditor}
        readOnly={readOnly}
        fieldType={fieldType}
      />
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
