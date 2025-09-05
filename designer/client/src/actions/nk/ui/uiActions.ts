import type { EditState } from "../../../components/graph/node-modal/node/useNodeState";
import type { SwitchToolTipsHighlightAction } from "../tooltips";
import type { LayoutActions } from "./layout";

export type UiActions =
    | SwitchToolTipsHighlightAction
    | LayoutActions
    | { type: "SET_PENDING_CHANGES"; id: string; pendingChanges?: EditState };
