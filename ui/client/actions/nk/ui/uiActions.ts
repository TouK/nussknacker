import {CloseModalsAction} from "../closeModals"
import {EditGroupAction, ToggleGroupAction, UnGroupAction} from "../groups"
import {SwitchToolTipsHighlightAction} from "../tooltips"
import {LayoutChangedAction, TogglePanelAction} from "./layout"

export type UiActions =
    | CloseModalsAction
    | EditGroupAction
    | SwitchToolTipsHighlightAction
    | ToggleGroupAction
    | TogglePanelAction
    | LayoutChangedAction
    | UnGroupAction
