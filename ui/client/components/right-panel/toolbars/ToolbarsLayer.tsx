import React, {useMemo, useEffect, EffectCallback, memo} from "react"
import {DragDropContext, DropResult} from "react-beautiful-dnd"
import {Toolbar} from "../RightToolPanels"
import {ToolbarsSide} from "../../../reducers/toolbars"
import {useDispatch} from "react-redux"
import {moveToolbar, registerToolbars} from "../../../actions/nk/toolbars"
import {ToolbarsPanel} from "./ToolbarsPanel"

export const ToolbarDraggableType = "TOOLBAR"

function useMemoizedIds<T extends { id: string }>(array: T[]): string {
  return useMemo(() => array.map(v => v.id).join(), [array])
}

function useIdsEffect<T extends { id: string }>(effect: EffectCallback, array) {
  const [hash] = useMemoizedIds(array)
  return useEffect(effect, [hash])
}

function ToolbarsLayer(props: { toolbars: Toolbar[] }) {
  const dispatch = useDispatch()
  const {toolbars} = props

  useIdsEffect(() => {dispatch(registerToolbars(toolbars))}, toolbars)

  const onDragEnd = ((result: DropResult) => {
    const {destination, type, reason, source} = result
    if (reason === "DROP" && type === ToolbarDraggableType && destination) {
      dispatch(moveToolbar(
        [source.droppableId, source.index],
        [destination.droppableId, destination.index],
      ))
    }
  })

  return (
    <DragDropContext onDragEnd={onDragEnd}>
      <ToolbarsPanel availableToolbars={toolbars} side={ToolbarsSide.TopRight}/>
      <ToolbarsPanel availableToolbars={toolbars} side={ToolbarsSide.BottomRight}/>
    </DragDropContext>
  )
}

export default memo(ToolbarsLayer)
