import {css, cx} from "@emotion/css"
import React, {PropsWithChildren, useRef} from "react"
import {useDrag, useDrop} from "react-dnd"
import {ReactComponent as Handlebars} from "../../../../assets/img/handlebars.svg"

const TYPE = "field"

interface MovableRowProps {
  index: number,
  moveItem: (a: number, b: number) => void,
}

interface DragItem {
  index: number,
  type: string,
}

export function MovableRow(props: PropsWithChildren<MovableRowProps>): JSX.Element {
  const {index, moveItem, children} = props
  const ref = useRef(null)

  const [{isDragging}, drag, preview] = useDrag(() => ({
    type: TYPE,
    item: {index},
  }))

  const [{handlerId}, drop] = useDrop({
    accept: TYPE,
    collect: (monitor) => ({
      handlerId: monitor.getHandlerId(),
    }),
    hover: (item: DragItem, monitor) => {
      if (!ref.current) {
        return
      }
      const dragIndex = item.index
      const hoverIndex = index

      // Don't replace items with themselves
      if (dragIndex === hoverIndex) {
        return
      }

      // Determine rectangle on screen
      const hoverBoundingRect = ref.current?.getBoundingClientRect()

      // Get vertical middle
      const hoverMiddleY =
        (hoverBoundingRect.bottom - hoverBoundingRect.top) / 2

      // Determine mouse position
      const clientOffset = monitor.getClientOffset()

      // Get pixels to the top
      const hoverClientY = clientOffset.y - hoverBoundingRect.top

      // Only perform the move when the mouse has crossed half of the items height
      // When dragging downwards, only move when the cursor is below 50%
      // When dragging upwards, only move when the cursor is above 50%

      // Dragging downwards
      if (dragIndex < hoverIndex && hoverClientY < hoverMiddleY) {
        return
      }

      // Dragging upwards
      if (dragIndex > hoverIndex && hoverClientY > hoverMiddleY) {
        return
      }

      // Time to actually perform the action
      moveItem(dragIndex, hoverIndex)

      // Note: we're mutating the monitor item here!
      // Generally it's better to avoid mutations,
      // but it's good here for the sake of performance
      // to avoid expensive index searches.
      item.index = hoverIndex
    },
  })

  drop(drag(ref))

  return (
    <div
      ref={ref}
      className={cx("movable-row", css({
        opacity: isDragging ? 0.5 : 1,
        display: "grid",
        gridTemplateColumns: "1fr auto",
        alignItems: "center",
      }))}
      data-handler-id={handlerId}
    >
      {children}
      <Handlebars className={"handle-bars"}/>
    </div>
  )
}
