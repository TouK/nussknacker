import {getToolbarsInitData} from "../../reducers/selectors/toolbars"
import {ToolbarsSide} from "../../reducers/toolbars"
import {ThunkAction} from "../reduxTypes"
import {Toolbar} from "../../components/toolbarComponents/toolbar"

export type ToolbarPosition = [ToolbarsSide | string, number]

type ResetToolbarsAction = { type: "RESET_TOOLBARS", toolbars: Array<[string, ToolbarsSide]> }
type RegisterToolbarsAction = { type: "REGISTER_TOOLBARS", toolbars: Array<[string, ToolbarsSide]>, configId: string }
type MoveToolbarAction = { type: "MOVE_TOOLBAR", from: ToolbarPosition, to: ToolbarPosition }
type ToggleToolbarAction = { type: "TOGGLE_TOOLBAR", id: string, isCollapsed: boolean }
type ToggleToolboxGroupAction = { type: "TOGGLE_NODE_TOOLBOX_GROUP", nodeGroup: string }
type ToggleAllToolbarsAction = { type: "TOGGLE_ALL_TOOLBARS", isCollapsed: boolean }

export const toggleAllToolbars = (isCollapsed: boolean): ToggleAllToolbarsAction => ({type: "TOGGLE_ALL_TOOLBARS", isCollapsed})

export const resetToolbars = (): ThunkAction => {
  return (dispatch, getState) => {
    const toolbars = getToolbarsInitData(getState())
    dispatch({type: "RESET_TOOLBARS", toolbars})
  }
}

export function registerToolbars(toolbars: Toolbar[], configId: string): RegisterToolbarsAction {
  return {
    type: "REGISTER_TOOLBARS",
    toolbars: toolbars.map(({id, defaultSide}) => [id, defaultSide]),
    configId,
  }
}

export function moveToolbar(from: ToolbarPosition, to: ToolbarPosition): MoveToolbarAction {
  return {
    type: "MOVE_TOOLBAR",
    from, to,
  }
}

export function toggleToolbar(id: string, isCollapsed = false): ToggleToolbarAction {
  return {
    type: "TOGGLE_TOOLBAR",
    id, isCollapsed,
  }
}

export function toggleToolboxGroup(nodeGroup: string): ToggleToolboxGroupAction {
  return {
    type: "TOGGLE_NODE_TOOLBOX_GROUP",
    nodeGroup,
  }
}

export type ToolbarActions =
  | ResetToolbarsAction
  | RegisterToolbarsAction
  | MoveToolbarAction
  | ToggleToolbarAction
  | ToggleAllToolbarsAction
  | ToggleToolboxGroupAction
