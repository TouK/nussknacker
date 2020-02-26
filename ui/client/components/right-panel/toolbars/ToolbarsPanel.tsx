import {ToolbarsSide} from "../../../reducers/toolbars"
import {Toolbar} from "../RightToolPanels"
import {Droppable} from "react-beautiful-dnd"
import {DraggablePanel} from "../DraggablePanel"
import React from "react"
import {useSelector} from "react-redux"
import {RootState} from "../../../reducers/index"
import {ToolbarDraggableType} from "./ToolbarsLayer"

function sortByIdsFrom(orderElement: string[]) {
  return ({id: a}, {id: b}) => orderElement.findIndex(v => v === a) - orderElement.findIndex(v => v === b)
}

type OwnProps = {
  side: ToolbarsSide,
  availableToolbars: Toolbar[],
  className?: string,
}

export function ToolbarsPanel(props: OwnProps) {
  const {side, availableToolbars, className} = props
  const order = useSelector<RootState, string[]>(s => s.toolbars[side] || [])

  return (
    <Droppable droppableId={side} type={ToolbarDraggableType}>
      {provided => (
        <div ref={provided.innerRef} {...provided.droppableProps} className={className}>
          {availableToolbars
            .filter(({id}) => order.includes(id))
            .sort(sortByIdsFrom(order))
            .map(({id, component, noDrag}, index) => (
              <DraggablePanel key={id} id={id} index={index} disabled={noDrag}>{component}</DraggablePanel>
            ))}
          {provided.placeholder}
        </div>
      )}
    </Droppable>
  )
}



