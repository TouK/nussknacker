import {css, cx} from "@emotion/css"
import React, {DetailedHTMLProps, HTMLAttributes} from "react"
import {DndProvider} from "react-dnd"
import {HTML5Backend} from "react-dnd-html5-backend"

type Props = DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>

export default function DragArea({children, className, ...props}: Props): JSX.Element {
  return (
    <div className={cx(css({width: "100%", height: "100%"}), className)} {...props}>
      <DndProvider backend={HTML5Backend}>
        {children}
      </DndProvider>
    </div>
  )
}
