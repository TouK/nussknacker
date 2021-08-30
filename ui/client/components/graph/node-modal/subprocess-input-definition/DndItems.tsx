import {css} from "emotion"
import update from "immutability-helper"
import {cloneDeep} from "lodash"
import React, {useCallback, useRef} from "react"
import {Draggable, DraggableChildrenFn} from "react-beautiful-dnd"
import {DragHandlerContext} from "../../../toolbarComponents/DragHandle"
import {DragHandle} from "./DragHandle"
import {DropTarget} from "./DropTarget"
import {FakeFormWindow} from "./FakeFormWindow"
import {ItemsProps} from "./Items"

interface DndListProps<I> extends ItemsProps<I> {
  onChange: (value: I[]) => void,
}

export function DndItems<I>(props: DndListProps<I>): JSX.Element {
  const {items, onChange} = props

  const moveItem = useCallback((source: number, target: number) => {
    if (source >= 0 && target >= 0) {
      const previousFields = cloneDeep(items.map(({item}) => item))
      const newFields = update(previousFields, {
        $splice: [[source, 1], [target, 0, previousFields[source]]],
      })
      onChange(newFields)
    }
  }, [items, onChange])

  const droppableId = useRef(Date.now().toString())

  const renderDraggable: DraggableChildrenFn = useCallback(
    (p, s, r) => {
      const {el} = items[r.source.index]
      return (
        <div
          {...p.draggableProps}
          ref={p.innerRef}
          className={css({
            display: "grid",
            gridTemplateColumns: "1fr auto",
            filter: s.isDragging ? "drop-shadow(0px 2px 6px rgba(0, 0, 0, .5))" : "none",
          })}
        >
          {el}
          <DragHandlerContext.Provider value={p.dragHandleProps}>
            <div {...p.dragHandleProps}>
              <DragHandle active={s.isDragging}/>
            </div>
          </DragHandlerContext.Provider>
        </div>
      )
    },
    [items],
  )

  return (
    <DropTarget
      droppableId={droppableId.current}
      renderClone={renderDraggable}
      CloneWrapper={FakeFormWindow}
      onDragEnd={({destination, source}) => moveItem(source?.index, destination?.index)}
    >
      {items.map((_, index) => (
        <Draggable key={index} draggableId={`${index}`} index={index}>
          {renderDraggable}
        </Draggable>
      ))}
    </DropTarget>
  )

}
