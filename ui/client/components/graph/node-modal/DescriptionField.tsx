/* eslint-disable i18next/no-literal-string */
import {NodeField} from "./NodeField"
import {FieldType} from "./editors/field/Field"
import React from "react"
import {NodeFieldProps} from "./NodeDetailsContentProps3"

export type DescriptionFieldProps = Omit<NodeFieldProps<"additionalFields.description", string>, "fieldLabel" | "fieldType" | "fieldProperty">

export const DescriptionField = ({
  autoFocus,
  defaultValue,
  isMarked,
  renderFieldLabel,
  setProperty,
  editedNode,
  isEditMode,
  showValidation,
  readonly,
  validators,
}: DescriptionFieldProps): JSX.Element => {
  return (
    <NodeField
      autoFocus={autoFocus}
      defaultValue={defaultValue}
      isMarked={isMarked}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
      editedNode={editedNode}
      isEditMode={isEditMode}
      showValidation={showValidation}
      readonly={readonly}
      validators={validators}
      fieldType={FieldType.plainTextarea}
      fieldLabel={"Description"}
      fieldProperty={"additionalFields.description"}
    />
  )
}
