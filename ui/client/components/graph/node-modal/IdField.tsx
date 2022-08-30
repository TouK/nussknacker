import {allValid, mandatoryValueValidator, uniqueValueValidator} from "./editors/Validators"
import Field, {FieldType} from "./editors/field/Field"
import React, {useMemo} from "react"
import {useDiffMark} from "./PathsToMark"
import {NodeType} from "../../../types"
import {useSelector} from "react-redux"
import {getProcessNodesIds} from "../../../reducers/selectors/graph"

interface IdFieldProps {
  isEditMode?: boolean,
  node: NodeType,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty?: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
}

// wise decision to treat a name as an id forced me to do so.
// now we have consisten id for validation, branch params etc
const FAKE_NAME_PROP_NAME = "$name"

export function applyIdFromFakeName({id, ...editedNode}: NodeType & { [FAKE_NAME_PROP_NAME]?: string }): NodeType {
  const name = editedNode[FAKE_NAME_PROP_NAME]
  delete editedNode[FAKE_NAME_PROP_NAME]
  return {...editedNode, id: name ?? id}
}

export function IdField({
  isEditMode,
  node,
  renderFieldLabel,
  setProperty,
  showValidation,
}: IdFieldProps): JSX.Element {
  const nodes = useSelector(getProcessNodesIds)
  const otherNodes = useMemo(() => nodes.filter(n => n !== node.id), [node.id, nodes])

  const validators = [mandatoryValueValidator, uniqueValueValidator(otherNodes)]
  const [isMarked] = useDiffMark()
  const propName = `id`
  const value = useMemo(() => node[FAKE_NAME_PROP_NAME] ?? node[propName], [node, propName])
  const marked = useMemo(() => isMarked(FAKE_NAME_PROP_NAME) || isMarked(propName), [isMarked, propName])
  return (
    <Field
      type={FieldType.input}
      isMarked={marked}
      showValidation={showValidation}
      onChange={(newValue) => setProperty(FAKE_NAME_PROP_NAME, newValue.toString())}
      readOnly={!isEditMode}
      className={!showValidation || allValid(validators, [value]) ? "node-input" : "node-input node-input-with-error"}
      validators={validators}
      value={value}
      autoFocus
    >
      {renderFieldLabel("Name")}

    </Field>
  )
}
