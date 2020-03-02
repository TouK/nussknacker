import {CollapsibleToolbar} from "./right-panel/toolbars/CollapsibleToolbar"
import ToolBox from "./ToolBox"
import React from "react"

export function CreatorPanel({writeAllowed}: { writeAllowed: boolean }) {
  return (
    <CollapsibleToolbar id="CREATOR-PANEL" title={"Creator panel"} isHidden={!writeAllowed}>
      <ToolBox/>
    </CollapsibleToolbar>
  )
}
