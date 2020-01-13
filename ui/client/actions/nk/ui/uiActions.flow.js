// @flow
import type {EditGroupAction} from "../groups"
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
import type {ToggleProcessActionModalAction} from "../toggleProcessActionDialog"

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