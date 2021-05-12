import {CloseModalsAction} from "../closeModals"
import {EditGroupAction, ToggleGroupAction, UnGroupAction} from "../groups"
import {DisplayModalEdgeDetailsAction, DisplayModalNodeDetailsAction} from "../modal"
import {SwitchToolTipsHighlightAction} from "../tooltips"
import {LayoutChangedAction, TogglePanelAction} from "./layout"

export type UiActions =
    | CloseModalsAction
    | DisplayModalEdgeDetailsAction
    | DisplayModalNodeDetailsAction
    | EditGroupAction
    | SwitchToolTipsHighlightAction
    | ToggleGroupAction
    | TogglePanelAction
    | LayoutChangedAction
    | UnGroupAction
