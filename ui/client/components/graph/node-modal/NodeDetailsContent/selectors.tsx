import {createSelector, createSelectorCreator, defaultMemoize} from "reselect"
import {getProcessCategory, getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"
import ProcessUtils from "../../../../common/ProcessUtils"
import {RootState} from "../../../../reducers"
import {AdditionalPropertiesConfig, NodeId, NodeType, NodeValidationError, UIParameter} from "../../../../types"
import {isEqual} from "lodash"

const createDeepEqualSelector = createSelectorCreator(
  defaultMemoize,
  isEqual
)

const getProcessDefinition = createSelector(getProcessDefinitionData, s => s.processDefinition)
export const getAdditionalPropertiesConfig = createSelector(getProcessDefinitionData, s => (s.additionalPropertiesConfig || {}) as AdditionalPropertiesConfig)
const getNodeResults = createSelector(getProcessToDisplay, process => ProcessUtils.getNodeResults(process))
export const getFindAvailableBranchVariables = createSelector(
  getNodeResults,
  nodeResults => ProcessUtils.findVariablesForBranches(nodeResults)
)
const getNodeResult = createSelector(getNodeResults, s => (nodeId) => s?.[nodeId])
const getNodeDetails = createDeepEqualSelector(
  (state: RootState) => state.nodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails[nodeId]
)

export const getValidationPerformed = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId): boolean => nodeDetails(nodeId)?.validationPerformed
)
const getValidationErrors = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId)?.validationErrors
)
export const getDetailsParameters = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId): UIParameter[] => {
    const parameters = nodeDetails(nodeId)?.parameters
    return parameters || null
  }
)
export const getResultParameters = createSelector(
  getNodeResult,
  (nodeResult) => (nodeId) => nodeResult(nodeId)?.parameters || null
)
export const getExpressionType = createSelector(
  getNodeDetails,
  (nodeDetails) => (nodeId) => nodeDetails(nodeId)?.expressionType
)
export const getNodeTypingInfo = createSelector(
  getNodeResult,
  (nodeResult) => (nodeId) => nodeResult(nodeId)?.typingInfo
)
export const getNodeExpressionType = createSelector(
  getExpressionType, getNodeTypingInfo, (expressionType, nodeTypingInfo) => (nodeId) => ({
    fields: expressionType(nodeId)?.fields || nodeTypingInfo(nodeId),
  })
)
export const getProcessProperties = createSelector(getProcessToDisplay, s => s.properties)
export const getProcessId = createSelector(getProcessToDisplay, s => s.id)
export const getCurrentErrors = createSelector(
  getValidationPerformed,
  getValidationErrors,
  (validationPerformed, validationErrors) => (originalNodeId: NodeId, nodeErrors: NodeValidationError[] = []) => validationPerformed(originalNodeId) ? validationErrors(originalNodeId) : nodeErrors
)
export const getDynamicParameterDefinitions = createSelector(
  getValidationPerformed, getDetailsParameters, getResultParameters, getProcessDefinitionData, (validationPerformed, detailsParameters, resultParameters, {processDefinition}) => (node: NodeType) => {
    const dynamicParameterDefinitions = validationPerformed(node.id) ? detailsParameters(node.id) : resultParameters(node.id)
    if (!dynamicParameterDefinitions) {
      return ProcessUtils.findNodeObjectTypeDefinition(node, processDefinition)?.parameters
    }
    return dynamicParameterDefinitions || null
  }
)

export const getDynamicOutputParameterDefinitions = createSelector(
    getValidationPerformed, getDetailsParameters, getResultParameters, getProcessDefinitionData, (validationPerformed, detailsParameters, resultParameters, {processDefinition}) => (node: NodeType) => {
        const dynamicParameterDefinitions = validationPerformed(node.id) ? detailsParameters(node.id) : resultParameters(node.id)
        if (!dynamicParameterDefinitions) {
            return ProcessUtils.findNodeObjectTypeDefinition(node, processDefinition)?.outputParameters
        }

        return dynamicParameterDefinitions || null
    }
)

export const getFindAvailableVariables = createSelector(
  getProcessDefinition,
  getProcessCategory,
  getProcessToDisplay,
  (processDefinition, processCategory, processToDisplay) => ProcessUtils.findAvailableVariables(processDefinition, processCategory, processToDisplay)
)
export const getVariableTypes = createSelector(
  getNodeResults,
  (nodeResults) => (originalNodeId) => ProcessUtils.getVariablesFromValidation(nodeResults, originalNodeId) || {}
)
