import {CloseModalsAction} from "../closeModals"
import {SwitchToolTipsHighlightAction} from "../tooltips"
import {LayoutChangedAction, TogglePanelAction} from "./layout"

export type UiActions =
    | CloseModalsAction
    | SwitchToolTipsHighlightAction
    | TogglePanelAction
    | LayoutChangedAction
