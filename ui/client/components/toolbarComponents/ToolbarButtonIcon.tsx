import SvgDiv from "../SvgDiv"
import React, {memo} from "react"
import * as path from "path"

function ToolbarButtonIcon({icon, ...props}: { icon: JSX.Element | string, title: string, className?: string }) {
  return (
    typeof icon !== "string" ?
      <div {...props}>{icon}</div> :
      icon.endsWith(".svg") ?
        <SvgDiv {...props} svgFile={path.join("buttons", icon)}/> :
        <div {...props} dangerouslySetInnerHTML={{__html: icon}}/>
  )
}

export default memo(ToolbarButtonIcon)
