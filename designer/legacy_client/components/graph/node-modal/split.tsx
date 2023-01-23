import {NodeType} from "../../../types"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {IdField} from "./IdField"
import {DescriptionField} from "./DescriptionField"
import React from "react"

export function Split({
  isEditMode,
  node,
  renderFieldLabel,
  setProperty,
  showValidation,
}: {
  isEditMode?: boolean,
  node: NodeType,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
}): JSX.Element {
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <DescriptionField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}
