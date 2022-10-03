import {css} from "@emotion/css"
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
  disabled?: boolean,
  onChange: (value: I[]) => void,
}

export function DndItems<I>(props: DndListProps<I>): JSX.Element {
  const {items, onChange, disabled} = props

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
    (p, s, r) => (
      <div
        {...p.draggableProps}
        ref={p.innerRef}
        className={css({
          display: "grid",
          gridTemplateColumns: "1fr auto",
          filter: s.isDragging ? "drop-shadow(0px 2px 6px rgba(0, 0, 0, .5))" : "none",
        })}
        data-testid={`draggable:${r.source.index}`}
      >
        <DragHandlerContext.Provider value={p.dragHandleProps}>
          {items[r.source.index].el}
          {!disabled && <DragHandle active={s.isDragging} provided={p.dragHandleProps}/>}
        </DragHandlerContext.Provider>
      </div>
    ),
    [disabled, items],
  )

  return (
    <DropTarget
      droppableId={droppableId.current}
      renderClone={renderDraggable}
      CloneWrapper={FakeFormWindow}
      onDragEnd={({destination, source}) => moveItem(source?.index, destination?.index)}
    >
      {items.map((_, index) => (
        <Draggable key={index} draggableId={`${index}`} index={index} isDragDisabled={disabled}>
          {renderDraggable}
        </Draggable>
      ))}
    </DropTarget>
  )

}
