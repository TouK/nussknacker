import React, {PropsWithChildren, useContext, createContext} from "react"
import {DraggableProvidedDragHandleProps} from "react-beautiful-dnd"

export const DragHandlerContext = createContext<DraggableProvidedDragHandleProps>(null)

export function useDragHandler() {
  const handleProps = useContext(DragHandlerContext)
  if (!handleProps) {
    // eslint-disable-next-line i18next/no-literal-string
    throw new Error("used outside ToolbarPanel")
  }
  return handleProps
}

export function DragHandle({children, className}: PropsWithChildren<{ className?: string }>) {
  const handleProps = useDragHandler()

  return (
    <div {...handleProps} className={className}>
      {children}
    </div>
  )
}
