import Field from "./editors/field/Field"
import {allValid} from "./editors/Validators"
import {get} from "lodash"
import React from "react"
import {NodeFieldProps} from "./NodeDetailsContentProps3"
import {useDiffMark} from "./PathsToMark"

export function NodeField<N extends string, V>({
  fieldType,
  fieldLabel,
  fieldProperty,
  autoFocus,
  readonly,
  defaultValue,
  validators = [],
  renderFieldLabel,
  setProperty,
  editedNode,
  isEditMode,
  showValidation,
}: NodeFieldProps<N, V>): JSX.Element {
  const readOnly = !isEditMode || readonly
  const value = get(editedNode, fieldProperty, null) ?? defaultValue
  const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"
  const onChange = (newValue) => setProperty(fieldProperty, newValue, defaultValue)
  const [isMarked] = useDiffMark()

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
