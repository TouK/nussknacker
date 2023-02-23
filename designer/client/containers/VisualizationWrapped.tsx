import React from "react"
import Visualization from "./Visualization"
import {useParams} from "react-router-dom"

export default function VisualizationWrapped(): JSX.Element {
  const {processId} = useParams<{ processId: string }>()
  return (
    <Visualization processId={decodeURIComponent(processId)}/>
  )
}
