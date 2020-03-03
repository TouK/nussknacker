import {CollapsibleToolbar} from "./right-panel/toolbars/CollapsibleToolbar"
import ToolBox from "./ToolBox"
import React from "react"
import {useSelector} from "react-redux"
import {getCapabilities} from "./right-panel/selectors/other"

export function CreatorPanel() {
  const capabilities = useSelector(getCapabilities)

  return (
    <CollapsibleToolbar id="CREATOR-PANEL" title={"Creator panel"} isHidden={!capabilities.write}>
      <ToolBox/>
    </CollapsibleToolbar>
  )
}
