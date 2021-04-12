import React, {useMemo, useEffect, EffectCallback, useState, useCallback} from "react"
import {DragDropContext, DropResult} from "react-beautiful-dnd"
import {ToolbarsSide} from "../../reducers/toolbars"
import {useDispatch} from "react-redux"
import {moveToolbar, registerToolbars} from "../../actions/nk/toolbars"
import {ToolbarsContainer} from "./ToolbarsContainer"
import cn from "classnames"

import styles from "./ToolbarsLayer.styl"
import {SidePanel, PanelSide} from "../sidePanels/SidePanel"
import {Toolbar} from "./toolbar"

function useMemoizedIds<T extends { id: string }>(array: T[]): string {
  return useMemo(() => array.map(v => v.id).join(), [array])
}

function useIdsEffect<T extends { id: string }>(effect: EffectCallback, array) {
  const [hash] = useMemoizedIds(array)
  return useEffect(effect, [hash])
}

export const ToolbarDraggableType = "TOOLBAR"

function ToolbarsLayer(props: { toolbars: Toolbar[] }): JSX.Element {
  const dispatch = useDispatch()
  const {toolbars} = props

  const [isDragging, setIsDragging] = useState(false)

  useIdsEffect(() => {
    dispatch(registerToolbars(toolbars))
  }, toolbars)

  const onDragEnd = useCallback((result: DropResult) => {
    setIsDragging(false)
    const {destination, type, reason, source} = result
    if (reason === "DROP" && type === ToolbarDraggableType && destination) {
      dispatch(moveToolbar(
        [source.droppableId, source.index],
        [destination.droppableId, destination.index],
      ))
    }
  }, [dispatch])

  const onDragStart = useCallback(() => {setIsDragging(true)}, [])

  return (
    <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart}>

      <SidePanel side={PanelSide.Left} className={cn(styles.left, isDragging && styles.isDraggingStarted)}>
        <ToolbarsContainer
          availableToolbars={toolbars}
          side={ToolbarsSide.TopLeft}
          className={cn(styles.top)}
        />
        <ToolbarsContainer
          availableToolbars={toolbars}
          side={ToolbarsSide.BottomLeft}
          className={cn(styles.bottom)}
        />
      </SidePanel>

      <SidePanel side={PanelSide.Right} className={cn(styles.right, isDragging && styles.isDraggingStarted)}>
        <ToolbarsContainer
          availableToolbars={toolbars}
          side={ToolbarsSide.TopRight}
          className={cn(styles.top)}
        />
        <ToolbarsContainer
          availableToolbars={toolbars}
          side={ToolbarsSide.BottomRight}
          className={cn(styles.bottom)}
        />
      </SidePanel>

    </DragDropContext>
  )
}

export default ToolbarsLayer

