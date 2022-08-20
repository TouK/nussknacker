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
const getNodeDetails = (state: RootState) => (nodeId) => {
  const nodeDetails = state.nodeDetails
  return nodeDetails[nodeId] || {}
}
const getValidationPerformed = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId).validationPerformed
)
const getValidationErrors = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId).validationErrors
)
const getDetailsParameters = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId).parameters
)
const getResultParameters = createSelector(
  getNodeResult,
  (nodeResult) => (nodeId) => nodeResult(nodeId)?.parameters
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
  getValidationPerformed,
  getDetailsParameters,
  getResultParameters,
  (validationPerformed, detailsParameters, resultParameters) => (nodeId) => {

    const validationPerformed1 = validationPerformed(nodeId)
    const detailsParameters1 = detailsParameters(nodeId)
    const newVar = resultParameters(nodeId) || null

    console.log(validationPerformed1, detailsParameters1, newVar)
    return validationPerformed1 ? detailsParameters1 :
      //for some cases e.g. properties parameters is undefined, we replace it with null no to care about undefined in comparisons
      newVar
  }
)
