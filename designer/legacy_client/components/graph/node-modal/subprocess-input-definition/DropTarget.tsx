import {ReactComponentLike} from "prop-types"
import React, {PropsWithChildren, useCallback} from "react"
import {DragDropContext, DragDropContextProps, DraggableChildrenFn, Droppable, DroppableProps} from "react-beautiful-dnd"

export function DropTarget({
  children,
  renderClone,
  CloneWrapper = "div",
  onDragEnd,
  ...props
}: PropsWithChildren<{CloneWrapper?: ReactComponentLike} & Pick<DragDropContextProps, "onDragEnd"> & Omit<DroppableProps, "children">>): JSX.Element {
  const clone: DraggableChildrenFn = useCallback(
    (...args) => <CloneWrapper>{renderClone(...args)}</CloneWrapper>,
    [CloneWrapper, renderClone],
  )
  return (
    <DragDropContext onDragEnd={onDragEnd}>
      <Droppable {...props} renderClone={clone}>
        {(p) => (
          <div ref={p.innerRef}>
            <div {...p.droppableProps}>
              {children}
              {p.placeholder}
            </div>
          </div>
        )}
      </Droppable>
    </DragDropContext>
  )
}
