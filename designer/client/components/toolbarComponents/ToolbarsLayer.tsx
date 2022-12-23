import React, {useMemo, useEffect, EffectCallback, useState, useCallback} from "react"
import {DragDropContext, DropResult} from "react-beautiful-dnd"
import {ToolbarsSide} from "../../reducers/toolbars"
import {useDispatch, useSelector} from "react-redux"
import {moveToolbar, registerToolbars} from "../../actions/nk/toolbars"
import {ToolbarsContainer} from "./ToolbarsContainer"
import cn from "classnames"

import styles from "./ToolbarsLayer.styl"
import {SidePanel, PanelSide} from "../sidePanels/SidePanel"
import {Toolbar} from "./toolbar"
import {getCapabilities} from "../../reducers/selectors/other"
import {useUserSettings} from "../../common/userSettings"
import {SURVEY_CLOSED_SETTINGS_KEY} from "../toolbars/SurveyPanel"

function useMemoizedIds<T extends { id: string }>(array: T[]): string {
  return useMemo(() => array.map(v => v.id).join(), [array])
}

function useIdsEffect<T extends { id: string }>(effect: EffectCallback, array) {
  const [hash] = useMemoizedIds(array)
  return useEffect(effect, [hash])
}

export const ToolbarDraggableType = "TOOLBAR"

export function useToolbarsVisibility(toolbars: Toolbar[]) {
  const {editFrontend} = useSelector(getCapabilities)
  const [userSettings] = useUserSettings()
  const hiddenToolbars = useMemo(
    () => ({
      "survey-panel": userSettings[SURVEY_CLOSED_SETTINGS_KEY],
      "creator-panel": !editFrontend,
    }),
    [editFrontend, userSettings]
  )

  return useMemo(
    () => toolbars.map((t) => ({...t, isHidden: hiddenToolbars[t.id]})),
    [hiddenToolbars, toolbars]
  )
}

function ToolbarsLayer(props: { toolbars: Toolbar[], configId: string }): JSX.Element {
  const dispatch = useDispatch()
  const {toolbars, configId} = props

  const [isDragging, setIsDragging] = useState(false)

  useEffect(() => {
    dispatch(registerToolbars(toolbars, configId))
  }, [dispatch, toolbars, configId])

  const onDragEnd = useCallback((result: DropResult) => {
    setIsDragging(false)
    const {destination, type, reason, source} = result
    if (reason === "DROP" && type === ToolbarDraggableType && destination) {
      dispatch(moveToolbar(
        [source.droppableId, source.index],
        [destination.droppableId, destination.index],
        configId
      ))
    }
  }, [configId, dispatch])

  const onDragStart = useCallback(() => {
    setIsDragging(true)
  }, [])

  const availableToolbars = useToolbarsVisibility(toolbars)

  return (
    <DragDropContext onDragEnd={onDragEnd} onDragStart={onDragStart}>

      <SidePanel side={PanelSide.Left} className={cn(styles.left, isDragging && styles.isDraggingStarted)}>
        <ToolbarsContainer
          availableToolbars={availableToolbars}
          side={ToolbarsSide.TopLeft}
          className={cn(styles.top)}
        />
        <ToolbarsContainer
          availableToolbars={availableToolbars}
          side={ToolbarsSide.BottomLeft}
          className={cn(styles.bottom)}
        />
      </SidePanel>

      <SidePanel side={PanelSide.Right} className={cn(styles.right, isDragging && styles.isDraggingStarted)}>
        <ToolbarsContainer
          availableToolbars={availableToolbars}
          side={ToolbarsSide.TopRight}
          className={cn(styles.top)}
        />
        <ToolbarsContainer
          availableToolbars={availableToolbars}
          side={ToolbarsSide.BottomRight}
          className={cn(styles.bottom)}
        />
      </SidePanel>

    </DragDropContext>
  )
}

export default ToolbarsLayer

