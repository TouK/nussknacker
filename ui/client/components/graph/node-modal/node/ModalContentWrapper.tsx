import React from "react"
import Draggable from "react-draggable"

export function ModalContentWrapper({children}) {
  return (
    <div className="draggable-container">
      <Draggable bounds="parent" handle=".modal-draggable-handle">
        <div className="espModal" data-testid="node-modal">
          {children}
        </div>
      </Draggable>
    </div>
  )
}
