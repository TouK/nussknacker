import {FieldType} from "./editors/field/Field"
import React from "react"
import {NodeField} from "./NodeField"
import {NodeFieldProps} from "./NodeDetailsContentProps3"

export type DisableFieldProps = Omit<NodeFieldProps<"isDisabled", string>, "fieldType" | "fieldLabel" | "fieldProperty">

export function DisableField({
  autoFocus,
  defaultValue,
  renderFieldLabel,
  setProperty,
  editedNode,
  isEditMode,
  showValidation,
  readonly,
  validators,
}: DisableFieldProps): JSX.Element {
  return (
    <NodeField
      autoFocus={autoFocus}
      defaultValue={defaultValue}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
      editedNode={editedNode}
      isEditMode={isEditMode}
      showValidation={showValidation}
      readonly={readonly}
      validators={validators}
      fieldType={FieldType.checkbox}
      fieldLabel={"Disabled"}
      fieldProperty={"isDisabled"}
    />
  )
}
