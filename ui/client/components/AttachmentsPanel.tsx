import {CollapsibleToolbar} from "./right-panel/toolbars/CollapsibleToolbar"
import ProcessAttachments from "./ProcessAttachments"
import React from "react"

export function AttachmentsPanel() {
  return (
    <CollapsibleToolbar id="ATTACHMENTS-PANEL" title={"Attachments"}>
      <ProcessAttachments/>
    </CollapsibleToolbar>
  )
}
