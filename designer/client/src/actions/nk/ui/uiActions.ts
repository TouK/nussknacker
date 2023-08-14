import { SwitchToolTipsHighlightAction } from "../tooltips";
import { LayoutChangedAction, TogglePanelAction } from "./layout";

export type UiActions = SwitchToolTipsHighlightAction | TogglePanelAction | LayoutChangedAction;
