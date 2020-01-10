// @flow
import type {
  AddNodeToGroupAction,
  CloseModalsAction,
  DisplayModalEdgeDetailsAction,
  DisplayModalNodeDetailsAction,
  SwitchToolTipsHighlightAction,
  ToggleConfirmDialogAction,
  ToggleGroupAction,
  ToggleInfoModalAction,
  ToggleModalDialogAction,
  TogglePanelAction,
  UnGroupAction,
} from "../index"
import type {EditGroupAction} from "../process/editGroup"
import type {ToggleProcessActionModalAction} from "../process/toggleProcessActionDialog"

export type UiActions =
    | AddNodeToGroupAction
    | CloseModalsAction
    | DisplayModalEdgeDetailsAction
    | DisplayModalNodeDetailsAction
    | EditGroupAction
    | SwitchToolTipsHighlightAction
    | ToggleConfirmDialogAction
    | ToggleGroupAction
    | ToggleInfoModalAction
    | ToggleModalDialogAction
    | TogglePanelAction
    | ToggleProcessActionModalAction
    | UnGroupAction