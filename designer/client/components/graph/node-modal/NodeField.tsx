import Field, {FieldType} from "./editors/field/Field"
import {allValid, Validator} from "./editors/Validators"
import {get} from "lodash"
import React from "react"
import {useDiffMark} from "./PathsToMark"
import {NodeType} from "../../../types"

type NodeFieldProps<N extends string, V> = {
  autoFocus?: boolean,
  defaultValue?: V,
  fieldLabel: string,
  fieldProperty: N,
  fieldType: FieldType,
  isEditMode?: boolean,
  node: NodeType,
  readonly?: boolean,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
  validators?: Validator[],
}

export function NodeField<N extends string, V>({
  autoFocus,
  defaultValue,
  fieldLabel,
  fieldProperty,
  fieldType,
  isEditMode,
  node,
  readonly,
  renderFieldLabel,
  setProperty,
  showValidation,
  validators = [],
}: NodeFieldProps<N, V>): JSX.Element {
  const readOnly = !isEditMode || readonly
  const value = get(node, fieldProperty, null) ?? defaultValue
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
