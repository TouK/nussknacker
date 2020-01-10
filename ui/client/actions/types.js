// @flow

import type {Store as ReduxStore} from "redux"
import type {
  AddNodeToGroupAction,
  CloseModalsAction,
  DisplayModalEdgeDetailsAction,
  DisplayModalNodeDetailsAction,
  LoggedUserAction,
  ReportEventAction,
  SwitchToolTipsHighlightAction,
  ToggleConfirmDialogAction,
  ToggleGroupAction,
  ToggleInfoModalAction,
  ToggleModalDialogAction,
  TogglePanelAction,
  UiSettingsAction,
  UnGroupAction,
} from "./esp"
import type {AvailableQueryStatesAction} from "./esp/process/availableQueryStates"
import type {EditGroupAction} from "./esp/process/editGroup"
import type {ProcessDefinitionDataAction} from "./esp/process/processDefinitionData"
import type {ToggleProcessActionModalAction} from "./esp/process/toggleProcessActionDialog"

type ActionTypes =
    | "TOGGLE_CONFIRM_DIALOG"
    | "USER_TRACKING"
    | "LAYOUT_CHANGED"
    | "TOGGLE_LEFT_PANEL"
    | "TOGGLE_RIGHT_PANEL"
    | "LAYOUT"
    | "BUSINESS_VIEW_CHANGED"
    | "START_GROUPING"
    | "CANCEL_GROUPING"
    | "FINISH_GROUPING"
    | "ADD_NODE_TO_GROUP"
    | "UNGROUP"
    | "EXPAND_GROUP"
    | "COLLAPSE_GROUP"
    | "LOGGED_USER"
    | "UI_SETTINGS"
    | "PROCESS_DEFINITION_DATA"
    | "AVAILABLE_QUERY_STATES"
    | "SWITCH_TOOL_TIPS_HIGHLIGHT"
    | "ZOOM_IN"
    | "ZOOM_OUT"
    | "HANDLE_HTTP_ERROR"
    | "DISPLAY_NODE_DETAILS"
    | "DELETE_NODES"
    | "NODES_CONNECTED"
    | "NODES_DISCONNECTED"
    | "NODE_ADDED"
    | "NODES_WITH_EDGES_ADDED"
    | "VALIDATION_RESULT"
    | "URL_CHANGED"
    | "COPY_SELECTION"
    | "CUT_SELECTION"
    | "PASTE_SELECTION"
    | "DELETE_SELECTION"
    | "EXPAND_SELECTION"
    | "RESET_SELECTION"
    | "DISPLAY_MODAL_NODE_DETAILS"
    | "DISPLAY_MODAL_EDGE_DETAILS"
    | "CLOSE_MODALS"
    | "TOGGLE_MODAL_DIALOG"
    | "TOGGLE_INFO_MODAL"
    | "IMPORT_FILES"
    | "EXPORT_PROCESS_TO_JSON"
    | "EXPORT_PROCESS_TO_PDF"
    | "EDIT_NODE"
    | "SHOW_METRICS"
    | "UPDATE_TEST_CAPABILITIES"
    | "DISPLAY_PROCESS"
    | "DISPLAY_PROCESS_ACTIVITY"
    | "PROCESS_LOADING"
    | "LOADING_FAILED"
    | "UPDATE_IMPORTED_PROCESS"
    | "CLEAR_PROCESS"
    | "TOGGLE_PROCESS_ACTION_MODAL"
    | "DISPLAY_PROCESS_COUNTS"
    | "HIDE_RUN_PROCESS_DETAILS"
    | "DISPLAY_TEST_RESULTS_DETAILS"
    | "EDIT_GROUP"
    | "EDIT_EDGE"
    | "UNDO"
    | "REDO"
    | "CLEAR"

type UiActions =
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

type SettingsActions =
    | LoggedUserAction
    | UiSettingsAction
    | ProcessDefinitionDataAction
    | AvailableQueryStatesAction

export type Action =
    | ReportEventAction
    | UiActions
    | SettingsActions

export type State = $FlowTODO;
export type GetState = () => State;

type A = $Shape<{ type: ActionTypes, ... }> | Action
export type Dispatch = (action: A | ThunkAction | PromiseAction) => any;
export type ThunkAction = (dispatch: Dispatch, getState: GetState) => any;
export type PromiseAction = Promise<A>;

export type Store = ReduxStore<State, Action>;