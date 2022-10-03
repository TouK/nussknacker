// provide parents css classes to dragged clone - temporary.
import React, {PropsWithChildren} from "react"
import {NodeTable} from "../NodeDetailsContent/NodeTable"

export function FakeFormWindow({children}: PropsWithChildren<unknown>): JSX.Element {
  return (
    <div className="modalContentDark">
      <NodeTable className="fieldsControl">
        {children}
      </NodeTable>
    </div>
  )
}
