import React from "react"
import {NodeType} from "../../../../types"
import {IdField} from "../IdField"
import {NodeTableBody} from "./NodeTable"

export function NodeDetailsFallback(props: {
  node: NodeType,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  isEditMode?: boolean,
  showValidation?: boolean,
}): JSX.Element {
  return (
    <>
      <NodeTableBody>
        <IdField {...props}/>
      </NodeTableBody>
      <span>Node type not known.</span>
      <pre>{JSON.stringify(props.node, null, 2)}</pre>
    </>
  )
}
