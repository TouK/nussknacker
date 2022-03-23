import React from "react"
import {NodeType} from "../../../../types"

export function NodeDetails(props: { node: NodeType }): JSX.Element {
  return (
    <div>
      <pre>{JSON.stringify(props.node, null, 2)}</pre>
    </div>
  )
}
