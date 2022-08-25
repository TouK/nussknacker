import React from "react"
import {NodeType} from "../../../../types"

export function NodeDetailsFallback(props: { node: NodeType }): JSX.Element {
  return (
    <div>
      <span>Node type not known.</span>
      <pre>{JSON.stringify(props.node, null, 2)}</pre>
    </div>
  )
}
