import {FieldType} from "./editors/field/Field"
import React from "react"
import {NodeField} from "./NodeField"
import {NodeType} from "../../../types"
import {Validator} from "./editors/Validators"

interface DisableFieldProps {
  autoFocus?: boolean,
  defaultValue?: string,
  isEditMode?: boolean,
  node: NodeType,
  readonly?: boolean,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
  validators?: Validator[],
}

export function DisableField({
  autoFocus,
  defaultValue,
  isEditMode,
  node,
  readonly,
  renderFieldLabel,
  setProperty,
  showValidation,
  validators,
}: DisableFieldProps): JSX.Element {
  return (
    <NodeField
      autoFocus={autoFocus}
      defaultValue={defaultValue}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
      node={node}
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
