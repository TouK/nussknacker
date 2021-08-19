import React from "react"
import {useSelector} from "react-redux"
import {getFetchedProcessDetails} from "../reducers/selectors/graph"
import {useWindows} from "../windowManager"
import Visualization from "./Visualization"

export interface VisualizationProps {
  [key: string]: unknown,
}

// Visualization wrapped to make partial (for now) refactor to TS and hooks
export default function VisualizationWrapped(props: VisualizationProps): JSX.Element {
  const {editNode, editEdge} = useWindows()

  const fetchedProcessDetails = useSelector(getFetchedProcessDetails)

  return (
    <Visualization
      showModalNodeDetails={editNode}
      showModalEdgeDetails={editEdge}
      fetchedProcessDetails={fetchedProcessDetails}
      {...props}
    />
  )
}
