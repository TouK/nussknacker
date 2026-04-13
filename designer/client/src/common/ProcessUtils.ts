/* eslint-disable i18next/no-literal-string */
import { flatten, isEmpty, pickBy, transform } from "lodash";
import type { Scenario } from "src/components/Process/types";

import { StickyNoteDefinition, StickyNoteType } from "../components/graph/utils/stickyNotesUtils";
import type { ScenarioLabelValidationError } from "../components/Labels/types";
import type { RootState } from "../reducers";
import { getScenario } from "../reducers/selectors/graph";
import type { TypingResult, UIParameter } from "../types/definition";
import type { NodeId, NodeType } from "../types/node";
import type { ComponentDefinition, ReturnedType, ScenarioGraph } from "../types/scenarioGraph";
import type { NodeResults, ValidationErrors, ValidationResult, VariableTypes } from "../types/validation";
import { determineComponentId } from "./componentUtils";

class ProcessUtils {
    canExport = (state: RootState): boolean => {
        const scenario = getScenario(state);
        return isEmpty(scenario) ? false : !isEmpty(scenario.scenarioGraph.nodes);
    };

    isValidationResultPresent = (scenario: Scenario) => {
        return Boolean(scenario.validationResult);
    };

    //fixme maybe return hasErrors flag from backend?
    hasNeitherErrorsNorWarnings = (scenario: Scenario) => {
        return (
            this.isValidationResultPresent(scenario) &&
            this.hasNoErrors(scenario) &&
            this.hasNoTestCaseErrors(scenario) &&
            this.hasNoWarnings(scenario)
        );
    };

    extractInvalidNodes = (invalidNodes: Pick<ValidationResult, "warnings">) => {
        return flatten(
            Object.keys(invalidNodes || {}).map((key, _) =>
                invalidNodes[key].map((error) => {
                    return { error: error, key: key };
                }),
            ),
        );
    };

    hasNoErrors = (scenario: Scenario) => {
        const result = this.getValidationErrors(scenario);
        return (
            !result ||
            (Object.keys(result.invalidNodes || {}).length == 0 &&
                (result.globalErrors || []).length == 0 &&
                (result.processPropertiesErrors || []).length == 0)
        );
    };

    hasNoTestCaseErrors = (scenario: Scenario) => {
        const result = this.getValidationErrors(scenario);
        return !result || (result.testCasesValidationErrors ?? []).length === 0;
    };

    getValidationResult = (scenario: Scenario): ValidationResult =>
        scenario?.validationResult || {
            validationErrors: [],
            validationWarnings: [],
            nodeResults: {},
            nodeNames: {},
            errors: {
                globalErrors: [],
                processPropertiesErrors: [],
                invalidNodes: {},
                testCasesValidationErrors: {},
            },
        };

    hasNoWarnings = (scenario: Scenario) => {
        const warnings = this.getValidationResult(scenario).warnings;
        return isEmpty(warnings) || Object.keys(warnings.invalidNodes || {}).length == 0;
    };

    hasNoPropertiesErrors = (scenario: Scenario) => {
        return isEmpty(this.getValidationErrors(scenario)?.processPropertiesErrors);
    };

    getLabelsErrors = (scenario: Scenario): ScenarioLabelValidationError[] => {
        return this.getValidationResult(scenario)
            .errors.globalErrors.filter((e) => e.error.typ == "ScenarioLabelValidationError")
            .map((e) => <ScenarioLabelValidationError>{ label: e.error.fieldName, messages: [e.error.description] });
    };

    getValidationErrors(scenario: Scenario): ValidationErrors {
        return this.getValidationResult(scenario).errors;
    }

    findContextForBranch = (node: NodeType, branchId: string) => {
        return `$edge-${branchId}-${node.id}`;
    };

    findVariablesForBranches = (nodeResults: NodeResults) => (nodeId: NodeId) => {
        //we find all nodes matching pattern encoding branch and edge and extract branch id
        const escapedNodeId = this.escapeNodeIdForRegexp(nodeId);
        return transform(
            nodeResults || {},
            function (result, nodeResult, key: string) {
                const branch = key.match(new RegExp(`^\\$edge-(.*)-${escapedNodeId}$`));
                if (branch && branch.length > 1) {
                    result[branch[1]] = nodeResult.variableTypes;
                }
            },
            {},
        );
    };

    getNodeResults = (scenario: Scenario): NodeResults => this.getValidationResult(scenario).nodeResults;

    //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions#Escaping
    escapeNodeIdForRegexp = (id: string) => id && id.replace(/[.*+\-?^${}()|[\]\\]/g, "\\$&");

    findAvailableVariables =
        (components: Record<string, ComponentDefinition>, scenario: Scenario) =>
        (nodeId: NodeId, parameterDefinition?: UIParameter): VariableTypes => {
            const nodeResults = this.getNodeResults(scenario);
            const variablesFromValidation = this.getVariablesFromValidation(nodeResults, nodeId);
            const variablesForNode =
                variablesFromValidation || this._findVariablesDeclaredBeforeNode(nodeId, scenario.scenarioGraph, components);
            const variablesToHideForParam = parameterDefinition?.variablesToHide || [];
            const withoutVariablesToHide = pickBy(variablesForNode, (va, key) => !variablesToHideForParam.includes(key));
            const additionalVariablesForParam = parameterDefinition?.additionalVariables || {};
            return { ...withoutVariablesToHide, ...additionalVariablesForParam };
        };

    getVariablesFromValidation = (nodeResults: NodeResults, nodeId: string) => nodeResults?.[nodeId]?.variableTypes;

    _findVariablesDeclaredBeforeNode = (
        nodeId: NodeId,
        scenarioGraph: ScenarioGraph,
        components: Record<string, ComponentDefinition>,
    ): VariableTypes => {
        const previousNodes = this._findPreviousNodes(nodeId, scenarioGraph).reverse();
        return previousNodes.reduce<VariableTypes>((variables, previousNodeId) => {
            return this._findVariablesDefinedInProcess(previousNodeId, scenarioGraph, components, variables);
        }, {});
    };

    _findVariablesDefinedInProcess = (
        nodeId: NodeId,
        scenarioGraph: ScenarioGraph,
        components: Record<string, ComponentDefinition>,
        currentVariables: VariableTypes,
    ): VariableTypes => {
        const node = scenarioGraph.nodes.find((node) => node.id === nodeId);
        if (!node) {
            return currentVariables;
        }
        const componentDefinition = this.extractComponentDefinition(node, components);
        const clazzName = componentDefinition?.returnType;
        const unknown: ReturnedType = {
            display: "Unknown",
            type: "Unknown",
            refClazzName: "java.lang.Object",
            params: [],
        };
        switch (node.type) {
            case "Source": {
                return isEmpty(clazzName) ? currentVariables : { ...currentVariables, input: clazzName };
            }
            case "FragmentInputDefinition": {
                return (node.parameters || []).reduce<VariableTypes>((variables, param) => {
                    return { ...variables, [param.name]: param.typ };
                }, currentVariables);
            }
            case "Enricher": {
                return { ...currentVariables, [node.output]: clazzName };
            }
            case "CustomNode":
            case "Join": {
                return isEmpty(clazzName) ? currentVariables : { ...currentVariables, [node.outputVar]: clazzName };
            }
            case "VariableBuilder": {
                return { ...currentVariables, [node.varName]: unknown };
            }
            case "Variable": {
                if (node.operation === "UNSET") {
                    const variablesAfterUnset = { ...currentVariables };
                    for (const field of node.fields || []) {
                        const unsetName = `${field?.name || ""}`.trim().replace(/^#/, "");
                        if (unsetName) {
                            delete variablesAfterUnset[unsetName];
                        }
                    }
                    return variablesAfterUnset;
                }
                return { ...currentVariables, [node.varName]: unknown };
            }
            case "Switch": {
                return node.exprVal ? { ...currentVariables, [node.exprVal]: unknown } : currentVariables;
            }
            default: {
                return currentVariables;
            }
        }
    };

    extractComponentDefinition = (node: NodeType, components?: Record<string, ComponentDefinition>): ComponentDefinition | null => {
        return node?.type == StickyNoteType ? StickyNoteDefinition : components?.[determineComponentId(node)];
    };

    humanReadableType = (typingResult?: Pick<TypingResult, "display">): string | null => typingResult?.display || null;

    _findPreviousNodes = (nodeId: NodeId, scenarioGraph: ScenarioGraph): NodeId[] => {
        const nodeEdge = scenarioGraph.edges?.find((edge) => edge.to === nodeId);
        if (isEmpty(nodeEdge)) {
            return [];
        } else {
            const previousNodes = this._findPreviousNodes(nodeEdge.from, scenarioGraph);
            return [nodeEdge.from].concat(previousNodes);
        }
    };
}

export default new ProcessUtils();
