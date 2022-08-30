import {createSelector} from "reselect"
import {getCapabilities} from "../../../../reducers/selectors/other"
import {RootState} from "../../../../reducers"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {NodeId} from "../../../../types"

export const getErrors = createSelector(
  getProcessToDisplay,
  (state: RootState, nodeId: NodeId) => nodeId,
  (process, nodeId) => {
    const errors = process?.validationResult?.errors
    const validationErrors = nodeId ? errors?.invalidNodes[nodeId] : errors?.processPropertiesErrors
    return validationErrors || []
  },
)

export const getReadOnly = createSelector(
  (state: RootState, fromProps?: boolean) => fromProps,
  (state: RootState) => getCapabilities(state),
  (fromProps, capabilities) => fromProps || !capabilities.editFrontend,
)
