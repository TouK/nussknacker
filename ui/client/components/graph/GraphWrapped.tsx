import React, {forwardRef} from "react"
import {Process} from "../../types"
import {useWindows} from "../../windowManager"
import {commonState, Graph} from "./Graph"

export interface GraphProps extends ReturnType<typeof commonState> {
  processToDisplay: Process,

  [key: string]: unknown,
}

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef<Graph, GraphProps>(function GraphWrapped(props, forwardedRef): JSX.Element {
  const {openNodeWindow, editEdge} = useWindows()
  return (
    <Graph
      ref={forwardedRef}
      {...props}
      openNodeWindow={openNodeWindow}
      showModalEdgeDetails={editEdge}
    />
  )
})
