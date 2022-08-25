import {allValid, mandatoryValueValidator} from "./editors/Validators"
import Field, {FieldType} from "./editors/field/Field"
import React from "react"
import {useDiffMark} from "./PathsToMark"
import {NodeType} from "../../../types"

interface IdFieldProps {
  isEditMode?: boolean,
  node: NodeType,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
}

export function IdField({
  isEditMode,
  node,
  renderFieldLabel,
  setProperty,
  showValidation,
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
      className={!showValidation || allValid(validators, [node.id]) ? "node-input" : "node-input node-input-with-error"}
      validators={validators}
      value={node.id}
      autoFocus
    >
      {renderFieldLabel("Name")}

    </Field>
  )
}
