import SvgDiv from "../SvgDiv"
import React from "react"

export function PanelButtonIcon({icon, ...props}: { icon: string, title: string }) {
  return icon.endsWith(".svg") ?
    <SvgDiv {...props} svgFile={`buttons/${icon}`}/> :
    <div {...props} dangerouslySetInnerHTML={{__html: icon}}/>
}
