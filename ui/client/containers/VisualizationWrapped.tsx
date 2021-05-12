import React, {useCallback} from "react"
import {useDispatch} from "react-redux"
import {displayModalEdgeDetails, displayModalNodeDetails, EventInfo} from "../actions/nk"
import {Edge, NodeType} from "../types"
import Visualization from "./Visualization"

export interface VisualizationProps {
  [key: string]: unknown,
}

// Visualization wrapped to make partial (for now) refactor to TS and hooks
export default function VisualizationWrapped(props: VisualizationProps): JSX.Element {
  const dispatch = useDispatch()
  const showModalEdgeDetails = useCallback((edge: Edge) => dispatch(displayModalEdgeDetails(edge)), [dispatch])
  const showModalNodeDetails = useCallback((node: NodeType, readonly?: boolean, eventInfo?: EventInfo) => dispatch(displayModalNodeDetails(
    node,
    readonly,
    eventInfo,
  )), [dispatch])
  return <Visualization {...props} showModalNodeDetails={showModalNodeDetails} showModalEdgeDetails={showModalEdgeDetails}/>
}
