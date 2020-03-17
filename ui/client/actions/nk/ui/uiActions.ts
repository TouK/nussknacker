import {CloseModalsAction} from "../closeModals"
import {AddNodeToGroupAction, EditGroupAction, ToggleGroupAction, UnGroupAction} from "../groups"
import {DisplayModalEdgeDetailsAction, DisplayModalNodeDetailsAction, ToggleInfoModalAction, ToggleModalDialogAction} from "../modal"
import {ToggleProcessActionModalAction} from "../toggleProcessActionDialog"
import {SwitchToolTipsHighlightAction} from "../tooltips"
import {LayoutChangedAction, TogglePanelAction} from "./layout"
import {ToggleConfirmDialogAction} from "./toggleConfirmDialog"

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
    | LayoutChangedAction
    | ToggleProcessActionModalAction
    | UnGroupAction
