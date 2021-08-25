import React, {ForwardedRef, forwardRef} from "react"
import {useWindows} from "../../windowManager"
import {Graph} from "./Graph"

export interface GraphProps {
  [key: string]: unknown,
}

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef(function GraphWrapped(props: GraphProps, ref: ForwardedRef<Graph>): JSX.Element {
  const {openNodeWindow, editEdge} = useWindows()

  return (
    <Graph
      ref={ref}
      {...props}
      openNodeWindow={openNodeWindow}
      showModalEdgeDetails={editEdge}
    />
  )
})
