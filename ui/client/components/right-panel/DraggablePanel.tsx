import React, {PropsWithChildren} from "react"
import {Draggable} from "react-beautiful-dnd"

type Props = PropsWithChildren<{
  id: string,
  disabled?: boolean,
  index: number
}>

export function DraggablePanel({id, index, children, disabled}: Props) {
  return (
    <Draggable draggableId={id} index={index} isDragDisabled={disabled}>
      {provided => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
        >
          {children}
        </div>
      )}
    </Draggable>
  )
}
