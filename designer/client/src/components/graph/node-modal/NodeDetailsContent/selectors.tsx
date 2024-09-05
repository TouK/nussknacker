import { createSelector, createSelectorCreator, defaultMemoize } from "reselect";
import { getScenario, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import ProcessUtils from "../../../../common/ProcessUtils";
import { RootState } from "../../../../reducers";
import { NodeId, NodeType, NodeValidationError, UiScenarioProperties, UIParameter } from "../../../../types";
import { isEqual } from "lodash";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

const getComponentsDefinition = createSelector(getProcessDefinitionData, (s) => s.components);
export const getScenarioPropertiesConfig = createSelector(
    getProcessDefinitionData,
    (s) => (s.scenarioProperties || {}) as UiScenarioProperties,
);
const getNodeResults = createSelector(getScenario, (scenario) => ProcessUtils.getNodeResults(scenario));
export const getFindAvailableBranchVariables = createSelector(getNodeResults, (nodeResults) =>
    ProcessUtils.findVariablesForBranches(nodeResults),
);
const getNodeResult = createSelector(getNodeResults, (s) => (nodeId: string) => s?.[nodeId]);
export const getNodeDetails = createDeepEqualSelector(
    (state: RootState) => state.nodeDetails,
    (nodeDetails) => (nodeId: string) => nodeDetails[nodeId],
);

export const getValidationPerformed = createSelector(
    getNodeDetails,
    (nodeDetails) =>
        (nodeId): boolean =>
            nodeDetails(nodeId)?.validationPerformed,
);
const getValidationErrors = createSelector(getNodeDetails, (nodeDetails) => (nodeId) => nodeDetails(nodeId)?.validationErrors);
export const getDetailsParameters = createSelector(getNodeDetails, (nodeDetails) => (nodeId): UIParameter[] => {
    const parameters = nodeDetails(nodeId)?.parameters;
    return parameters || null;
});
export const getResultParameters = createSelector(
    getNodeResult,
    (nodeResult) => (nodeId: string) => nodeResult(nodeId)?.parameters || null,
);
export const getExpressionType = createSelector(getNodeDetails, (nodeDetails) => (nodeId: string) => nodeDetails(nodeId)?.expressionType);
export const getNodeTypingInfo = createSelector(getNodeResult, (nodeResult) => (nodeId: string) => nodeResult(nodeId)?.typingInfo);
export const getNodeExpressionType = createSelector(getExpressionType, getNodeTypingInfo, (expressionType, nodeTypingInfo) => (nodeId) => {
    const type = expressionType(nodeId);
    return {
        // FIXME: TypingResult is broken
        fields: (type && "fields" in type && type.fields) || nodeTypingInfo(nodeId),
    };
});
export const getProcessProperties = createSelector(getScenarioGraph, (s) => s.properties);
export const getProcessName = createSelector(getScenario, (s) => s.name);
export const getCurrentErrors = createSelector(
    getValidationPerformed,
    getValidationErrors,
    (validationPerformed, validationErrors) =>
        (originalNodeId: NodeId, nodeErrors: NodeValidationError[] = []) =>
            validationPerformed(originalNodeId) ? validationErrors(originalNodeId) : nodeErrors,
);
export const getDynamicParameterDefinitions = createSelector(
    getValidationPerformed,
    getDetailsParameters,
    getResultParameters,
    getComponentsDefinition,
    (validationPerformed, detailsParameters, resultParameters, components) => (node: NodeType) => {
        const dynamicParameterDefinitions = validationPerformed(node.id) ? detailsParameters(node.id) : resultParameters(node.id);
        if (!dynamicParameterDefinitions) {
            return ProcessUtils.extractComponentDefinition(node, components)?.parameters;
        }
        return dynamicParameterDefinitions || null;
    },
);

export const getFindAvailableVariables = createSelector(getComponentsDefinition, getScenario, (processDefinition, scenario) =>
    ProcessUtils.findAvailableVariables(processDefinition, scenario),
);
export const getVariableTypes = createSelector(
    getNodeResults,
    (nodeResults) => (originalNodeId) => ProcessUtils.getVariablesFromValidation(nodeResults, originalNodeId) || {},
);
