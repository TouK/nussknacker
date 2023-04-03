import Field, {FieldType} from "./editors/field/Field"
import {allValid, Validator} from "./editors/Validators"
import {get} from "lodash"
import React, {useCallback, useMemo} from "react"
import {useDiffMark} from "./PathsToMark"
import {NodeType} from "../../../types"

export enum FieldInputType {
  STRING,
  INTEGER,
}

type NodeFieldProps<N extends string, V> = {
  autoFocus?: boolean,
  defaultValue?: V,
  fieldLabel: string,
  fieldProperty: N,
  fieldType: FieldType,
  isEditMode?: boolean,
  placeholder?: string
  node: NodeType,
  readonly?: boolean,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  inputType?: FieldInputType,
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
  placeholder,
  node,
  readonly,
  renderFieldLabel,
  setProperty,
  inputType,
  showValidation,
  validators = [],
}: NodeFieldProps<N, V>): JSX.Element {
  const readOnly = !isEditMode || readonly
  const value = get(node, fieldProperty, null) ?? defaultValue
  const className = !showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"

  const defaultOnChange = (newValue) =>
    setProperty(fieldProperty, newValue, defaultValue)

  const integerOnChange = (newValue) => {
    if (!newValue) {
      setProperty(fieldProperty, defaultValue, defaultValue)
      return
    }
    const pattern = new RegExp("^[0-9]*$")
    if (pattern.test(newValue)) {
      const valueWithoutLeadingZeros = parseInt(newValue)
      setProperty(fieldProperty, valueWithoutLeadingZeros, defaultValue)
    }
  }

  const onChange = useMemo(() => {
    if (inputType === FieldInputType.INTEGER) {
      return integerOnChange
    } else {
      return defaultOnChange
    }
  }, [inputType])

  const [isMarked] = useDiffMark()

  return (
    <Field
      type={fieldType}
      isMarked={isMarked(`${fieldProperty}`)}
      readOnly={readOnly}
      placeholder={placeholder}
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
