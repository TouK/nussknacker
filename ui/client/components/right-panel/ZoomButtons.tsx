import cn from "classnames"
import SvgDiv from "../SvgDiv"
import React from "react"
import {useDispatch} from "react-redux"
import {zoomIn, zoomOut} from "../../actions/nk/zoom"
import {Graph} from "./ToolsLayer"
import {Reset} from "./Reset"

interface Props {
  graphGetter: () => Graph,
  className?: string,
}

export function ZoomButtons({className, graphGetter}: Props) {
  const dispatch = useDispatch()
  const onZoomIn = () => dispatch(zoomIn(graphGetter()))
  const onZoomOut = () => dispatch(zoomOut(graphGetter()))

  return (
    <div className={cn("zoom-in-out", className)}>
      <Reset/>
      <SvgDiv className="zoom" title={"zoom-in"} svgFile="buttons/zoomin.svg" onClick={onZoomIn}/>
      <SvgDiv className="zoom" title={"zoom-out"} svgFile="buttons/zoomout.svg" onClick={onZoomOut}/>
    </div>
  )
}
