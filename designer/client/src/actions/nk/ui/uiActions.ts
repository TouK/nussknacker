import type { EditState } from "../../../components/graph/node-modal/node/useNodeState";
import type { SwitchToolTipsHighlightAction } from "../tooltips";
import type { LayoutChangedAction, PanelActions } from "./layout";

export type UiActions =
    | SwitchToolTipsHighlightAction
    | PanelActions
    | LayoutChangedAction
    | {
          type: "SET_PENDING_CHANGES";
          id: string;
          pendingChanges?: EditState;
      };
