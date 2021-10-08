import {getToolbarsInitData} from "../../reducers/selectors/toolbars"
import {ToolbarsSide} from "../../reducers/toolbars"
import {ThunkAction} from "../reduxTypes"
import {Toolbar} from "../../components/toolbarComponents/toolbar"
import {WithId} from "../../types/common"
import {ToolbarsConfig} from "../../components/toolbarSettings/types"

export type ToolbarPosition = [ToolbarsSide | string, number]

type ResetToolbarsAction = { type: "RESET_TOOLBARS", toolbars: Array<[string, ToolbarsSide]>, configId: string }
type RegisterToolbarsAction = { type: "REGISTER_TOOLBARS", toolbars: Array<[string, ToolbarsSide]>, configId: string }
type MoveToolbarAction = { type: "MOVE_TOOLBAR", from: ToolbarPosition, to: ToolbarPosition, configId: string }
type ToggleToolbarAction = { type: "TOGGLE_TOOLBAR", id: string, isCollapsed: boolean, configId: string }
type ToggleToolboxGroupAction = { type: "TOGGLE_COMPONENT_GROUP_TOOLBOX", componentGroup: string, configId: string }
type ToggleAllToolbarsAction = { type: "TOGGLE_ALL_TOOLBARS", isCollapsed: boolean, configId: string }
type ProcessToolbarsConfigurationAction = { type: "PROCESS_TOOLBARS_CONFIGURATION_LOADED", data: WithId<ToolbarsConfig> }

export const toggleAllToolbars = (isCollapsed: boolean, configId: string): ToggleAllToolbarsAction => ({
  type: "TOGGLE_ALL_TOOLBARS",
  isCollapsed,
  configId,
})

export const resetToolbars = (configId: string): ThunkAction => {
  return (dispatch, getState) => {
    const toolbars = getToolbarsInitData(getState())
    dispatch({type: "RESET_TOOLBARS", toolbars, configId})
  }
}

export function registerToolbars(toolbars: Toolbar[], configId: string): RegisterToolbarsAction {
  return {
    type: "REGISTER_TOOLBARS",
    toolbars: toolbars.map(({id, defaultSide}) => [id, defaultSide]),
    configId,
  }
}

export function moveToolbar(from: ToolbarPosition, to: ToolbarPosition, configId: string): MoveToolbarAction {
  return {
    type: "MOVE_TOOLBAR",
    from, to,
    configId,
  }
}

export function toggleToolbar(id: string , configId: string, isCollapsed = false): ToggleToolbarAction {
  return {
    type: "TOGGLE_TOOLBAR",
    id, isCollapsed,
    configId,
  }
}

export function toggleToolboxGroup(componentGroup: string, configId: string): ToggleToolboxGroupAction {
  return {
    type: "TOGGLE_COMPONENT_GROUP_TOOLBOX",
    componentGroup,
    configId,
  }
}

export type ToolbarActions =
  | ResetToolbarsAction
  | RegisterToolbarsAction
  | MoveToolbarAction
  | ToggleToolbarAction
  | ToggleAllToolbarsAction
  | ToggleToolboxGroupAction
  | ProcessToolbarsConfigurationAction
