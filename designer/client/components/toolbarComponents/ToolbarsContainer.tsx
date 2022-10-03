import {ToolbarsSide} from "../../reducers/toolbars"
import {
  Droppable,
  Draggable,
  DraggableChildrenFn,
  DraggableStateSnapshot,
  DroppableProvided,
  DroppableStateSnapshot,
  DraggableProvided,
  DraggableRubric,
  DraggableLocation,
} from "react-beautiful-dnd"
import React, {useCallback, CSSProperties, useMemo} from "react"
import {useSelector} from "react-redux"
import {ToolbarDraggableType} from "./ToolbarsLayer"
import styles from "./ToolbarsLayer.styl"
import cn from "classnames"
import {DragHandlerContext} from "./DragHandle"
import {getOrderForPosition} from "../../reducers/selectors/toolbars"
import {Toolbar} from "./toolbar"

interface Rubric extends DraggableRubric {
  source: DraggableLocation,
}

const fixAnimation = (style: CSSProperties) => ({...style, transitionDuration: `${0.0001}s`})
const getStyle = (style: CSSProperties, s: DraggableStateSnapshot) => s.isDropAnimating && s.draggingOver ? fixAnimation(style) : style

function sortByIdsFrom(orderElement: string[]) {
  return ({id: a}, {id: b}) => orderElement.findIndex(v => v === a) - orderElement.findIndex(v => v === b)
}

type Props = {
  side: ToolbarsSide,
  availableToolbars: Toolbar[],
  className?: string,
}

export function ToolbarsContainer(props: Props): JSX.Element {
  const {side, availableToolbars, className} = props
  const selector = useMemo(() => getOrderForPosition(side), [side])
  const order = useSelector(selector)

  const ordered = availableToolbars
    .filter(({id}) => order.includes(id))
    .sort(sortByIdsFrom(order))

  const renderDraggable: DraggableChildrenFn = useCallback(
    (p: DraggableProvided, s: DraggableStateSnapshot, r: Rubric) => (
      <div
        ref={p.innerRef}
        {...p.draggableProps}
        style={getStyle(p.draggableProps.style, s)}
        className={cn([
          styles.draggable,
          s.isDragging && styles.isDragging,
          s.draggingOver && styles.isDraggingOver,
          s.isDropAnimating && styles.isAnimating,
          r.source.index === 0 && styles.first,
          r.source.index === ordered.length - 1 && styles.last,
        ])}
      >
        <DragHandlerContext.Provider value={p.dragHandleProps}>
          <div className={styles.background}>
            <div className={styles.content}>
              {ordered[r.source.index].component}
            </div>
            <div className={styles.handler} {...p.dragHandleProps}/>
          </div>
        </DragHandlerContext.Provider>
      </div>
    ),
    [ordered],
  )

  const renderDroppable = useCallback(
    (p: DroppableProvided, s: DroppableStateSnapshot) => (
      <div
        ref={p.innerRef}
        className={cn([
          styles.droppable,
          s.isDraggingOver && styles.isDraggingOver,
          s.draggingFromThisWith && styles.isDraggingFrom,
          className,
        ])}
      >
        <div {...p.droppableProps} className={cn(styles.draggableList)}>
          <div className={styles.background}>
            {ordered.map(({id, component, isDragDisabled}, index) => (
              <Draggable key={id} draggableId={id} index={index} isDragDisabled={isDragDisabled}>
                {renderDraggable}
              </Draggable>
            ))}
            {p.placeholder}
          </div>
        </div>
      </div>
    ),
    [className, ordered, renderDraggable],
  )

  return (
    <Droppable droppableId={side} type={ToolbarDraggableType} renderClone={renderDraggable}>{renderDroppable}</Droppable>
  )
}
