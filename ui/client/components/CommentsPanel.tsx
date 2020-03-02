import {CollapsibleToolbar} from "./right-panel/toolbars/CollapsibleToolbar"
import ProcessComments from "./ProcessComments"
import React from "react"

export function CommentsPanel() {
  return (
    <CollapsibleToolbar id="COMMENTS-PANEL" title={"Comments"}>
      <ProcessComments/>
    </CollapsibleToolbar>
  )
}
