import {createSelector} from "reselect"
import {getNodeToDisplay, getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {getCapabilities} from "../../../../reducers/selectors/other"

export const getErrors = createSelector(
  getProcessToDisplay,
  getNodeToDisplay,
  (process, node) => {
    const errors = process?.validationResult?.errors
    const validationErrors = node?.id ? errors?.invalidNodes[node.id] : errors?.processPropertiesErrors
    return validationErrors || []
  },
)

export const getReadOnly = createSelector(
  (state, fromProps?: boolean) => fromProps,
  // _ needed for wierd reselect typings ;)
  (state, _) => getCapabilities(state),
  (fromProps, capabilities) => fromProps || !capabilities.editFrontend,
)
