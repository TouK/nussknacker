import {allValid, mandatoryValueValidator} from "./editors/Validators"
import Field, {FieldType} from "./editors/field/Field"
import React from "react"
import {IdFieldProps} from "./NodeDetailsContentProps3"
import {useDiffMark} from "./PathsToMark"

export function IdField({
  isEditMode,
  showValidation,
  editedNode,
  setProperty,
  renderFieldLabel,
}: IdFieldProps): JSX.Element {
  const validators = [mandatoryValueValidator]
  const [isMarked] = useDiffMark()
  return (
    <Field
      type={FieldType.input}
      isMarked={isMarked("id")}
      showValidation={showValidation}
      onChange={(newValue) => setProperty("id", newValue.toString())}
      readOnly={!isEditMode}
      className={!showValidation || allValid(validators, [editedNode.id]) ? "node-input" : "node-input node-input-with-error"}
      validators={validators}
      value={editedNode.id}
      autoFocus
    >
      {renderFieldLabel("Name")}

    </Field>
  )
}
