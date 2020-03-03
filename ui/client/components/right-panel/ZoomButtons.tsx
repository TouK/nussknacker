import cn from "classnames"
import SvgDiv from "../SvgDiv"
import React from "react"
import {useDispatch} from "react-redux"
import {zoomIn, zoomOut} from "../../actions/nk/zoom"
import {Graph} from "./ToolsLayer"

interface Props {
  graph: Graph,
  className?: string,
}

export function ZoomButtons({className, graph}: Props) {
  const dispatch = useDispatch()
  const onZoomIn = () => dispatch(zoomIn(graph))
  const onZoomOut = () => dispatch(zoomOut(graph))

  return (
    <div className={cn("zoom-in-out", className)}>
      <SvgDiv className="zoom" title={"zoom-in"} svgFile="buttons/zoomin.svg" onClick={onZoomIn}/>
      <SvgDiv className="zoom" title={"zoom-out"} svgFile="buttons/zoomout.svg" onClick={onZoomOut}/>
    </div>
  )
}
