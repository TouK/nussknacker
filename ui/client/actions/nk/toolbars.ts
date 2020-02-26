import {Toolbar} from "../../components/right-panel/RightToolPanels"
import {ToolbarsSide} from "../../reducers/toolbars"

export type ToolbarPosition = [ToolbarsSide | string, number]

type RegisterToolbarsAction = { type: "REGISTER_TOOLBARS", toolbars: Array<[string, ToolbarsSide]> }
type MoveToolbarAction = { type: "MOVE_TOOLBAR", from: ToolbarPosition, to: ToolbarPosition }

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

export type ToolbarActions =
  | RegisterToolbarsAction
  | MoveToolbarAction
