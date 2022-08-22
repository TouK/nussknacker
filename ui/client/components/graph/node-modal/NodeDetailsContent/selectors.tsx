import {createSelector} from "reselect"
import {getProcessCategory, getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"
import ProcessUtils from "../../../../common/ProcessUtils"
import {RootState} from "../../../../reducers"
import {AdditionalPropertiesConfig, NodeId, NodeValidationError} from "../../../../types"

const getProcessDefinition = createSelector(getProcessDefinitionData, s => s.processDefinition)
export const getAdditionalPropertiesConfig = createSelector(getProcessDefinitionData, s => (s.additionalPropertiesConfig || {}) as AdditionalPropertiesConfig)
const getValidationResult = createSelector(getProcessToDisplay, s => s?.validationResult)
const getNodeResults = createSelector(getValidationResult, s => s?.nodeResults)
export const getFindAvailableVariables = createSelector(
  getProcessDefinition,
  getProcessCategory,
  getProcessToDisplay,
  (processDefinition, processCategory, processToDisplay) => ProcessUtils.findAvailableVariables(processDefinition, processCategory, processToDisplay)
)
export const getFindAvailableBranchVariables = createSelector(
  getNodeResults,
  nodeResults => ProcessUtils.findVariablesForBranches(nodeResults)
)
const getNodeResult = createSelector(getNodeResults, s => (nodeId) => s?.[nodeId])
export const getNodeDetails = (state: RootState) => (nodeId) => {
  const nodeDetails = state.nodeDetails
  return nodeDetails[nodeId] || {}
}
export const getValidationPerformed = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId).validationPerformed
)
const getValidationErrors = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId).validationErrors
)
export const getDetailsParameters = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId)?.parameters || null
)
export const getResultParameters = createSelector(
  getNodeResult,
  (nodeResult) => (nodeId) => nodeResult(nodeId)?.parameters || null
)
export const getExpressionType = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId).expressionType
)
export const getNodeTypingInfo = createSelector(
  getNodeResult,
  (nodeResult) => (nodeId) => nodeResult(nodeId)?.typingInfo
)
export const getVariableTypes = createSelector(
  getNodeResult,
  (nodeResult) => (originalNodeId) => nodeResult(originalNodeId)?.variableTypes || {}
)
export const getProcessProperties = createSelector(getProcessToDisplay, s => s.properties)
export const getProcessId = createSelector(getProcessToDisplay, s => s.id)
export const getCurrentErrors = createSelector(
  getValidationPerformed,
  getValidationErrors,
  (validationPerformed, validationErrors) => (originalNodeId: NodeId, nodeErrors: NodeValidationError[] = []) => validationPerformed(originalNodeId) ? validationErrors(originalNodeId) : nodeErrors
)
export const getDynamicParameterDefinitions = createSelector(
  getValidationPerformed, getDetailsParameters, getResultParameters,
  (validationPerformed, detailsParameters, resultParameters) => (nodeId) => validationPerformed(nodeId) ?
    detailsParameters(nodeId) :
    //for some cases e.g. properties parameters is undefined, we replace it with null no to care about undefined in comparisons
    resultParameters(nodeId) || null
)
