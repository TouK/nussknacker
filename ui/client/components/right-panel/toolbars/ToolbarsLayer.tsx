import React, {useMemo, useEffect, EffectCallback, memo, useState} from "react"
import {DragDropContext, DropResult} from "react-beautiful-dnd"
import {ToolbarsSide} from "../../../reducers/toolbars"
import {useDispatch} from "react-redux"
import {moveToolbar, registerToolbars} from "../../../actions/nk/toolbars"
import {ToolbarsPanel} from "./ToolbarsPanel"
import cn from "classnames"
import {ScrollbarsExtended} from "../ScrollbarsExtended"

import styles from "./ToolbarsLayer.styl"
import styles2 from "../collipsableSidePanels.styl"
import {Toolbar} from "../Toolbars"
import {useRightPanelToggle, useLeftPanelToggle} from "../ToolsLayer"
import {Reset} from "../Reset"

function useMemoizedIds<T extends { id: string }>(array: T[]): string {
  return useMemo(() => array.map(v => v.id).join(), [array])
}

function useIdsEffect<T extends { id: string }>(effect: EffectCallback, array) {
  const [hash] = useMemoizedIds(array)
  return useEffect(effect, [hash])
}

export const ToolbarDraggableType = "TOOLBAR"

function ToolbarsLayer(props: { toolbars: Toolbar[] }) {
  const dispatch = useDispatch()
  const {toolbars} = props

  const [isDragging, setIsDragging] = useState(false)

  useIdsEffect(() => {dispatch(registerToolbars(toolbars))}, toolbars)

  const onDragEnd = (result: DropResult) => {
    setIsDragging(false)
    const {destination, type, reason, source} = result
    if (reason === "DROP" && type === ToolbarDraggableType && destination) {
      dispatch(moveToolbar(
        [source.droppableId, source.index],
        [destination.droppableId, destination.index],
      ))
    }
  }

  const {isOpenedRight} = useRightPanelToggle()
  const {isOpenedLeft} = useLeftPanelToggle()

  return (
    <>
      <DragDropContext onDragEnd={onDragEnd} onDragStart={() => {setIsDragging(true)}}>

        <div className={cn(styles2.collapsible, styles2.left, isOpenedLeft && styles2.isOpened)}>
          <ScrollbarsExtended>
            <div className={cn(styles.sidePanel, styles.left, isDragging && styles.isDraggingStarted)}>
              <ToolbarsPanel
                availableToolbars={toolbars}
                side={ToolbarsSide.TopLeft}
                className={cn(styles.top)}
              />
              <ToolbarsPanel
                availableToolbars={toolbars}
                side={ToolbarsSide.BottomLeft}
                className={cn(styles.bottom)}
              />
            </div>
          </ScrollbarsExtended>
        </div>

        <div className={cn(styles2.collapsible, styles2.right, isOpenedRight && styles2.isOpened)}>
          <ScrollbarsExtended>
            <div className={cn(styles.sidePanel, styles.right, isDragging && styles.isDraggingStarted)}>
              <ToolbarsPanel
                availableToolbars={toolbars}
                side={ToolbarsSide.TopRight}
                className={cn(styles.top)}
              />
              <ToolbarsPanel
                availableToolbars={toolbars}
                side={ToolbarsSide.BottomRight}
                className={cn(styles.bottom)}
              />
            </div>
          </ScrollbarsExtended>
        </div>

      </DragDropContext>
    </>
  )
}

export default memo(ToolbarsLayer)
