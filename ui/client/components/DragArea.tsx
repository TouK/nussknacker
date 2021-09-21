import {css} from "emotion"
import React, {PropsWithChildren} from "react"
import {DndProvider} from "react-dnd"
import {HTML5Backend} from "react-dnd-html5-backend"

export default function DragArea({children}: PropsWithChildren<unknown>): JSX.Element {
  return (
    <div className={css({width: "100%", height: "100%"})}>
      <DndProvider backend={HTML5Backend}>
        {children}
      </DndProvider>
    </div>
  )
}
