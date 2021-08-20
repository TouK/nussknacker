import React, {ForwardedRef, forwardRef} from "react"
import {displayModalEdgeDetails} from "../../actions/nk"
import {useWindows} from "../../windowManager"
import {Graph} from "./Graph"

export interface GraphProps {
  showModalEdgeDetails: typeof displayModalEdgeDetails,

  [key: string]: unknown,
}

// Graph wrapped to make partial (for now) refactor to TS and hooks
export default forwardRef(function GraphWrapped({showModalEdgeDetails, ...props}: GraphProps, ref: ForwardedRef<Graph>): JSX.Element {
  const {openNodeWindow} = useWindows()

  return (
    <Graph
      ref={ref}
      {...props}
      openNodeWindow={openNodeWindow}
      showModalEdgeDetails={showModalEdgeDetails}
    />
  )
})
