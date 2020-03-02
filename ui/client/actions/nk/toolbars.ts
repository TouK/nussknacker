import {Toolbar} from "../../components/right-panel/RightToolPanels"
import {ToolbarsSide} from "../../reducers/toolbars"

export type ToolbarPosition = [ToolbarsSide | string, number]

type RegisterToolbarsAction = { type: "REGISTER_TOOLBARS", toolbars: Array<[string, ToolbarsSide]> }
type MoveToolbarAction = { type: "MOVE_TOOLBAR", from: ToolbarPosition, to: ToolbarPosition }
type ToggleToolbarAction = { type: "TOGGLE_TOOLBAR", id: string, isCollapsed: boolean }
type ToggleToolboxGroupAction = { type: "TOGGLE_NODE_TOOLBOX_GROUP", nodeGroup: string }

export function registerToolbars(toolbars: Toolbar[]): RegisterToolbarsAction {
  return {
    type: "REGISTER_TOOLBARS",
    toolbars: toolbars.map(({id, defaultSide}) => [id, defaultSide]),
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
  | RegisterToolbarsAction
  | MoveToolbarAction
  | ToggleToolbarAction
  | ToggleToolboxGroupAction
