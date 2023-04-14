import {createSelector} from "reselect"
import {getCapabilities} from "../../../../reducers/selectors/other"
import {RootState} from "../../../../reducers"
import {getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {NodeId} from "../../../../types"
import ProcessUtils from "../../../../common/ProcessUtils"

export const getNodeErrors = createSelector(
  getProcessToDisplay,
  (state: RootState, nodeId: NodeId) => nodeId,
  (process, nodeId) => {
    return ProcessUtils.getValidationErrors(process)?.invalidNodes[nodeId] || []
  },
)

export const getPropertiesErrors = createSelector(
    getProcessToDisplay,
    (process) => ProcessUtils.getValidationErrors(process)?.processPropertiesErrors || [],
)

export const getReadOnly = createSelector(
  (state: RootState, fromProps?: boolean) => fromProps,
  (state: RootState) => getCapabilities(state),
  (fromProps, capabilities) => fromProps || !capabilities.editFrontend,
)
