import { SwitchToolTipsHighlightAction } from "../tooltips";
import { LayoutChangedAction, PanelActions } from "./layout";

export type UiActions = SwitchToolTipsHighlightAction | PanelActions | LayoutChangedAction;
