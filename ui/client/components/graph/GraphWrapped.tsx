import React from "react"
import {displayModalEdgeDetails, displayModalNodeDetails} from "../../actions/nk"
import {Graph} from "./Graph"

export interface GraphProps {
  showModalNodeDetails: typeof displayModalNodeDetails,
  showModalEdgeDetails: typeof displayModalEdgeDetails,

  [key: string]: unknown,
}

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default function GraphWrapped({showModalNodeDetails, showModalEdgeDetails, ...props}: GraphProps): JSX.Element {
  return (
    <Graph
      {...props}
      showModalNodeDetails={showModalNodeDetails}
      showModalEdgeDetails={showModalEdgeDetails}
    />
  )
}
