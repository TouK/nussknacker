import Field from "./editors/field/Field"
import {allValid} from "./editors/Validators"
import {get} from "lodash"
import React from "react"
import {NodeFieldProps} from "./NodeDetailsContentProps3"

export function NodeField<N extends string, V>(props: NodeFieldProps<N, V>): JSX.Element {
  const {
    fieldType,
    fieldLabel,
    fieldProperty,
    autoFocus,
    readonly,
    defaultValue,
    validators = [],
    isMarked,
    renderFieldLabel,
    setProperty,
    editedNode,
    isEditMode,
    showValidation,
  } = props

  const readOnly = !isEditMode || readonly
  const value = get(editedNode, fieldProperty, null) ?? defaultValue
  const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
  const onChange = (newValue) => setProperty(fieldProperty, newValue, defaultValue)
  return (
    <Field
      type={fieldType}
      isMarked={isMarked(`${fieldProperty}`)}
      readOnly={readOnly}
      showValidation={showValidation}
      autoFocus={autoFocus}
      className={className}
      validators={validators}
      value={value}
      onChange={onChange}
    >
      {renderFieldLabel(fieldLabel)}
    </Field>
  )
}
