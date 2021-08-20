import {get} from "lodash"
import {createSelector} from "reselect"
import ProcessUtils from "../../../../common/ProcessUtils"
import {getNodeToDisplay} from "../../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"

export const getNodeSettings = createSelector(
  getProcessDefinitionData,
  getNodeToDisplay,
  (process, node) => get(process.nodesConfig, ProcessUtils.findNodeConfigName(node)) || {},
)
