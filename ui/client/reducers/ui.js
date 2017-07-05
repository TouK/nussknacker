import _ from 'lodash'

const emptyUiState = {
  leftPanelIsOpened: false,
  showNodeDetailsModal: false,
  showEdgeDetailsModal: false,
  confirmDialog: {},
  modalDialog: {},
  expandedGroups: []
};

export function reducer(state = emptyUiState, action) {
  const withAllModalsClosed = (newState) => {
    const allModalsClosed = !(newState.modalDialog.openDialog || newState.showNodeDetailsModal || newState.showEdgeDetailsModal || newState.confirmDialog.isOpen)
    return { ...newState, allModalsClosed: allModalsClosed}
  }
  switch (action.type) {
    case "TOGGLE_LEFT_PANEL": {
      return withAllModalsClosed({
        ...state,
        leftPanelIsOpened: action.leftPanelIsOpened
      })
    }
    case "CLOSE_MODALS": {
      return withAllModalsClosed({
        ...state,
        showNodeDetailsModal: false,
        showEdgeDetailsModal: false
      })
    }
    case "DISPLAY_MODAL_NODE_DETAILS": {
      return withAllModalsClosed({
        ...state,
        showNodeDetailsModal: true
      })
    }
    case "DISPLAY_MODAL_EDGE_DETAILS": {
      return withAllModalsClosed({
        ...state,
        showEdgeDetailsModal: true
      })
    }
    case "TOGGLE_CONFIRM_DIALOG": {
      return withAllModalsClosed({
        ...state,
        confirmDialog: {
          isOpen: action.isOpen,
          text: action.text,
          onConfirmCallback: action.onConfirmCallback
        }
      })
    }
    case "TOGGLE_MODAL_DIALOG": {
      return withAllModalsClosed({
        ...state,
        modalDialog: {
          openDialog: action.openDialog
        }
      })
    }
    case "EXPAND_GROUP": {
      return {
        ...state,
        expandedGroups: _.concat(state.expandedGroups, [action.id])
      }
    }
    case "COLLAPSE_GROUP": {
      return {
        ...state,
        expandedGroups: state.expandedGroups.filter(g => g != action.id)
      }
    }
    case "EDIT_GROUP": {
      return {
        ...state,
        expandedGroups: state.expandedGroups.map(id => id == action.oldGroupId ? action.newGroup.id : id)
      }
    }
    default:
      return withAllModalsClosed(state)
  }
}
