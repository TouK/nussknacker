import { produce } from "immer";
import { isEqual } from "lodash";
import { createSelector, createSelectorCreator, lruMemoize as defaultMemoize } from "reselect";

import ProcessUtils from "../../../../common/ProcessUtils";
import type { RootState } from "../../../../reducers";
import { getProcessDefinitionData } from "../../../../reducers/selectors/getProcessDefinitionData";
import { getScenario, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import type { UIParameter } from "../../../../types/definition";
import type { NodeType, Parameter } from "../../../../types/node";
import type { PropertiesConfig, UiScenarioProperties } from "../../../../types/scenarioGraph";
import type { NodeValidationError, TestCaseValidationError } from "../../../../types/validation";
import { EditorType } from "../editors/expression/types";
import { getCurrentPropertiesErrors } from "../node/selectors";
import { determineParameterKey, OverrideKeys } from "../parameterHelpers";
import { appendPropertiesErrors, getScenarioPropertiesDef, isRequestSource } from "../requestSourceAddons";
import { getNodesDetails } from "./getNodeDetails";

const createDeepEqualSelector = createSelectorCreator(defaultMemoize, isEqual);

export const getComponentsDefinition = createSelector(getProcessDefinitionData, (s) => s.components);
export const getScenarioProperties = createSelector(getProcessDefinitionData, (s) => (s.scenarioProperties || {}) as UiScenarioProperties);
export const getScenarioPropertiesConfig = createSelector(getScenarioProperties, ({ propertiesConfig = {} as PropertiesConfig }) => {
    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const order = Object.keys(propertiesConfig).sort((a, b) => a.localeCompare(b));
    return { properties: propertiesConfig, order };
});

const getNodesResults = createSelector(getScenario, (scenario) => ProcessUtils.getNodeResults(scenario));
export const getFindAvailableBranchVariables = createSelector(getNodesResults, (nodeResults) =>
    ProcessUtils.findVariablesForBranches(nodeResults),
);
const getNodeResult = createSelector(getNodesResults, (s) => (nodeId: string) => s?.[nodeId]);

const getValidationPerformed = createDeepEqualSelector(getNodesDetails, (nodeDetails) => {
    return Object.fromEntries(Object.entries(nodeDetails).map(([k, { validationPerformed }]) => [k, validationPerformed]));
});

type TestCaseParams = { nodeId: string; testCaseName: string };
const getTestParams = (_: unknown, testCaseParams: TestCaseParams) => testCaseParams;

export const getValidationTestCasesErrors = createDeepEqualSelector(
    getNodesDetails,
    getTestParams,
    (nodeDetails, { nodeId, testCaseName }): TestCaseValidationError => {
        const testCasesValidationErrors = nodeDetails[nodeId]?.testCasesValidationErrors?.[testCaseName];

        return {
            assertionsErrors: testCasesValidationErrors?.assertionsErrors ?? {},
            enricherMockErrors: testCasesValidationErrors?.enricherMockErrors ?? [],
        };
    },
);

export const hasValidationTestCasesErrors = createDeepEqualSelector(
    getNodesDetails,
    getTestParams,
    (nodeDetails, { nodeId, testCaseName }): boolean => {
        const testCasesValidationErrors = nodeDetails[nodeId]?.testCasesValidationErrors?.[testCaseName];
        const hasAssertionErrors = Object.keys(testCasesValidationErrors?.assertionsErrors ?? {}).length > 0;
        const hasEnricherMockErrors = (testCasesValidationErrors?.enricherMockErrors ?? []).length > 0;

        return hasAssertionErrors || hasEnricherMockErrors;
    },
);

const getValidationErrors = createSelector(getNodesDetails, (nodeDetails) => (nodeId) => nodeDetails[nodeId]?.validationErrors);

export const getExpressionType = createSelector(getNodesDetails, (nodeDetails) => (nodeId: string) => nodeDetails[nodeId]?.expressionType);
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

const getCurrentErrors = createSelector(
    getValidationPerformed,
    getValidationErrors,
    (_: RootState, props: { node: NodeType; nodeErrors: NodeValidationError[] }) => props,
    (validationPerformed, validationErrors, { node, nodeErrors = [] }) =>
        validationPerformed[node.id] ? validationErrors(node.id) : nodeErrors,
);
export const getNodeErrors = createSelector(
    getCurrentErrors,
    getCurrentPropertiesErrors,
    (_: RootState, props: { node: NodeType }) => props.node,
    (currentErrors, propertiesErrors, node) => {
        if (isRequestSource(node)) {
            return appendPropertiesErrors(currentErrors, propertiesErrors);
        }
        return currentErrors;
    },
);

export const getDynamicParameterDefinitions = createDeepEqualSelector(
    (_: RootState, node: NodeType) => node,
    getNodesDetails,
    getNodesResults,
    getValidationPerformed,
    getComponentsDefinition,
    getScenarioPropertiesConfig,
    getUserSettings,
    (node, nodesDetails, nodesResults, validationPerformed, components, { order, properties }, userSettings) => {
        function getParameters(node: NodeType): UIParameter[] {
            const dynamicParameterDefinitions = validationPerformed[node.id]
                ? nodesDetails[node.id]?.parameters
                : nodesResults[node.id]?.parameters;

            const parameters = dynamicParameterDefinitions || ProcessUtils.extractComponentDefinition(node, components)?.parameters;

            if (isRequestSource(node)) {
                return [...parameters, ...getScenarioPropertiesDef(properties, order)];
            }

            return parameters;
        }

        function overridePrameters(parameters: UIParameter[] = [], node: NodeType): UIParameter[] {
            return produce(parameters, (draft) => {
                draft.forEach((param) => {
                    switch (determineParameterKey(node, param)) {
                        case OverrideKeys.HttpHeaders:
                        case OverrideKeys.HttpQueryParameters: {
                            param.typ = null;
                            param.editors = [{ type: EditorType.NAME_VALUE_LIST_EDITOR }];
                            break;
                        }
                    }
                });
            });
        }

        return overridePrameters(getParameters(node), node);
    },
);

export const getFindAvailableVariables = createSelector(getComponentsDefinition, getScenario, (processDefinition, scenario) =>
    ProcessUtils.findAvailableVariables(processDefinition, scenario),
);

export const getDynamicParametersChanged = createSelector(
    getNodesDetails,
    (_: RootState, { node }: { node: NodeType }) => node.id,
    (nodeDetails, nodeId) => {
        return nodeDetails[nodeId]?.changingDynamicParameters;
    },
);

export const getIsParameterStable = createSelector(
    getDynamicParametersChanged,
    (_: RootState, { param }: { param: Parameter }) => param.name,
    (changingDynamicParameters, paramName) => {
        return !changingDynamicParameters?.length || changingDynamicParameters.includes(paramName);
    },
);
