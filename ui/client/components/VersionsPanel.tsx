import {CollapsibleToolbar} from "./right-panel/toolbars/CollapsibleToolbar"
import ProcessHistory from "./ProcessHistory"
import React from "react"

export function VersionsPanel() {
  return (
    <CollapsibleToolbar id="VERSIONS-PANEL" title={"Versions"}>
      <ProcessHistory/>
    </CollapsibleToolbar>
  )
}
