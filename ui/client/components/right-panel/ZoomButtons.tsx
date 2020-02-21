import cn from "classnames"
import SvgDiv from "../SvgDiv"
import React from "react"

interface Props {
  onZoomIn: () => void,
  onZoomOut: () => void,
  className?: string,
}

export function ZoomButtons({className, onZoomIn, onZoomOut}: Props) {
  return (
    <div className={cn("zoom-in-out", className)}>
      <SvgDiv className="zoom" title={"zoom-in"} svgFile="buttons/zoomin.svg" onClick={onZoomIn}/>
      <SvgDiv className="zoom" title={"zoom-out"} svgFile="buttons/zoomout.svg" onClick={onZoomOut}/>
    </div>
  )
}
