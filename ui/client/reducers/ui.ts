import {GroupId, ProcessId} from "../actions/nk/models"
import {Action} from "../actions/reduxTypes"
import {DialogType, types} from "../components/modals/Dialogs"

export type UiState = {
  leftPanelIsOpened: boolean,
  rightPanelIsOpened: boolean,
  showNodeDetailsModal: boolean,
  showEdgeDetailsModal: boolean,
  confirmDialog: Partial<{
    isOpen: boolean,
    text: string,
    confirmText: string,
    denyText: string,
    onConfirmCallback: () => void,
  }>,
  modalDialog: Partial<{
    openDialog: DialogType,
    message: string,
    action: (processId: ProcessId, comment: string) => void,
    displayWarnings: boolean,
    text: string,
  }>,
  expandedGroups: GroupId[],
  allModalsClosed: boolean,
  isToolTipsHighlighted: boolean,
}

const emptyUiState: UiState = {
  allModalsClosed: true,
  leftPanelIsOpened: true,
  rightPanelIsOpened: true,
  showNodeDetailsModal: false,
  showEdgeDetailsModal: false,
  isToolTipsHighlighted: false,
  confirmDialog: {},
  modalDialog: {},
  expandedGroups: [],
}

function withAllModalsClosed(newState: UiState): UiState {
  const allModalsClosed = !(newState.modalDialog.openDialog || newState.showNodeDetailsModal || newState.showEdgeDetailsModal || newState.confirmDialog.isOpen)
  return {...newState, allModalsClosed}
}

export function reducer(state: UiState = emptyUiState, action: Action): UiState {
  switch (action.type) {
    case "TOGGLE_LEFT_PANEL": {
      return withAllModalsClosed({
        ...state,
        leftPanelIsOpened: !state.leftPanelIsOpened,
      })
    }
    case "TOGGLE_RIGHT_PANEL": {
      return withAllModalsClosed({
        ...state,
        rightPanelIsOpened: !state.rightPanelIsOpened,
      })
    }
    case "RESET_TOOLBARS": {
      return withAllModalsClosed({
        ...state,
        leftPanelIsOpened: true,
        rightPanelIsOpened: true,
      })
    }
    case "SWITCH_TOOL_TIPS_HIGHLIGHT": {
      return withAllModalsClosed({
        ...state,
        isToolTipsHighlighted: action.isHighlighted,
      })
    }
    case "CLOSE_MODALS": {
      return withAllModalsClosed({
        ...state,
        showNodeDetailsModal: false,
        showEdgeDetailsModal: false,
      })
    }
    case "DISPLAY_MODAL_NODE_DETAILS": {
      return withAllModalsClosed({
        ...state,
        showNodeDetailsModal: true,
      })
    }
    case "DISPLAY_MODAL_EDGE_DETAILS": {
      return withAllModalsClosed({
        ...state,
        showEdgeDetailsModal: true,
      })
    }
    case "TOGGLE_CONFIRM_DIALOG": {
      return withAllModalsClosed({
        ...state,
        confirmDialog: {
          isOpen: action.isOpen,
          text: action.text,
          confirmText: action.confirmText,
          denyText: action.denyText,
          onConfirmCallback: action.onConfirmCallback,
        },
      })
    }
    case "TOGGLE_MODAL_DIALOG": {
      return withAllModalsClosed({
        ...state,
        modalDialog: {
          openDialog: action.openDialog,
        },
      })
    }
    case "TOGGLE_INFO_MODAL": {
      return withAllModalsClosed({
        ...state,
        modalDialog: {
          openDialog: action.openDialog,
          text: action.text,
        },
      })
    }
    case "TOGGLE_PROCESS_ACTION_MODAL": {
      return withAllModalsClosed({
        ...state,
        modalDialog: {
          openDialog: types.processAction,
          message: action.message,
          action: action.action,
          displayWarnings: action.displayWarnings,
        },
      })
    }
    case "EXPAND_GROUP": {
      return {
        ...state,
        expandedGroups: [...state.expandedGroups, action.id],
      }
    }
    case "COLLAPSE_GROUP": {
      return {
        ...state,
        expandedGroups: state.expandedGroups.filter(id => id !== action.id),
      }
    }
    case "EDIT_GROUP": {
      return {
        ...state,
        expandedGroups: state.expandedGroups.map(id => id === action.oldGroupId ? action.newGroup.id : id),
      }
    }
    default:
      return withAllModalsClosed(state)
  }
}
