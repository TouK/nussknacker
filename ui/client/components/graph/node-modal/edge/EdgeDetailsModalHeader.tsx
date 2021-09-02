import React from "react"
import NkModalStyles from "../../../../common/NkModalStyles"

export function EdgeDetailsModalHeader(): JSX.Element {
  const titleStyles = NkModalStyles.headerStyles("#2D8E54", "white")
  return (
    <div className="modalHeader">
      <div className="edge-modal-title modal-draggable-handle" style={titleStyles}>
        <span>edge</span>
      </div>
    </div>
  )
}
