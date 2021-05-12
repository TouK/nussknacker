import {get} from "lodash"
import {createSelector} from "reselect"
import ProcessUtils from "../../../../common/ProcessUtils"
import {
  getGraph,
  isArchived,
  getNodeToDisplay,
  getProcessCategory,
  getProcessToDisplay,
  isBusinessView,
} from "../../../../reducers/selectors/graph"
import {getLoggedUser, getProcessDefinitionData} from "../../../../reducers/selectors/settings"

export const getErrors = createSelector(
  getProcessToDisplay,
  getNodeToDisplay,
  (process, node) => {
    const errors = process?.validationResult?.errors
    const validationErrors = node.id ? errors?.invalidNodes[node.id] : errors?.processPropertiesErrors
    return validationErrors || []
  },
)

export const getNodeSettings = createSelector(
  getProcessDefinitionData,
  getNodeToDisplay,
  (process, node) => get(process.nodesConfig, ProcessUtils.findNodeConfigName(node)) || {},
)

export const getReadOnly = createSelector(
  getGraph,
  getProcessCategory,
  getLoggedUser,
  isBusinessView,
  isArchived,
  (graph, category, user, isBusinessView, isArchived) => graph.nodeToDisplayReadonly ||
    !user.canWrite(category) ||
    isBusinessView ||
    isArchived,
)
