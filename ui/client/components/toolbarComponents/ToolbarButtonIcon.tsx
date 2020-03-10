import SvgDiv from "../SvgDiv"
import React, {memo} from "react"

function ToolbarButtonIcon({icon, ...props}: { icon: string, title: string, className?: string }) {
  return icon.endsWith(".svg") ?
    <SvgDiv {...props} svgFile={`buttons/${icon}`}/> :
    <div {...props} dangerouslySetInnerHTML={{__html: icon}}/>
}

export default memo(ToolbarButtonIcon)
