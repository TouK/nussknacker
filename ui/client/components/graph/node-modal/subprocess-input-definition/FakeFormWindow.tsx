// provide parents css classes to dragged clone - temporary.
import React from "react"

export function FakeFormWindow({children}): JSX.Element {
  return (
    <div className="modalContentDark">
      <div className="node-table fieldsControl">
        {children}
      </div>
    </div>
  )
}
