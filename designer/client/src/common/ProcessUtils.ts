/* eslint-disable i18next/no-literal-string */
import { flatten, isEmpty, pickBy, transform } from "lodash";
import type { Scenario } from "src/components/Process/types";

import { StickyNoteDefinition, StickyNoteType } from "../components/graph/utils/stickyNotesUtils";
import type {
    ComponentDefinition,
    NodeId,
    NodeResults,
    NodeType,
    ReturnedType,
    ScenarioGraph,
    TypingResult,
    UIParameter,
    ValidationResult,
    VariableTypes,
} from "../types";
import ProcessUtils2 from "./ProcessUtils2";

class ProcessUtils {
    extractInvalidNodes = (invalidNodes: Pick<ValidationResult, "warnings">) => {
        return flatten(
            Object.keys(invalidNodes || {}).map((key, _) =>
                invalidNodes[key].map((error) => {
                    return {
                        error: error,
                        key: key,
                    };
                }),
            ),
        );
    };

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

    findAvailableVariables =
        (components: Record<string, ComponentDefinition>, scenario: Scenario) =>
        (nodeId: NodeId, parameterDefinition?: UIParameter): VariableTypes => {
            const nodeResults = ProcessUtils2.getNodeResults(scenario);
            const variablesFromValidation = this.getVariablesFromValidation(nodeResults, nodeId);
            const variablesForNode =
                variablesFromValidation || this.findVariablesDeclaredBeforeNode(nodeId, scenario.scenarioGraph, components);
            const variablesToHideForParam = parameterDefinition?.variablesToHide || [];
            const withoutVariablesToHide = pickBy(variablesForNode, (va, key) => !variablesToHideForParam.includes(key));
            const additionalVariablesForParam = parameterDefinition?.additionalVariables || {};
            return { ...withoutVariablesToHide, ...additionalVariablesForParam };
        };

    getVariablesFromValidation = (nodeResults: NodeResults, nodeId: string) => nodeResults?.[nodeId]?.variableTypes;

    extractComponentDefinition = (node: NodeType, components?: Record<string, ComponentDefinition>): ComponentDefinition | null => {
        return node.type == StickyNoteType ? StickyNoteDefinition : components?.[this.determineComponentId(node)];
    };

    determineComponentId = (node?: NodeType): string | null => {
        const componentType = this.determineComponentType(node);
        const componentName = this.determineComponentName(node);
        return componentType && componentName ? `${componentType}-${componentName}` : null;
    };

    humanReadableType = (typingResult?: Pick<TypingResult, "display">): string | null => typingResult?.display || null;

    //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions#Escaping
    private escapeNodeIdForRegexp = (id: string) => id && id.replace(/[.*+\-?^${}()|[\]\\]/g, "\\$&");

    private findVariablesDeclaredBeforeNode = (
        nodeId: NodeId,
        scenarioGraph: ScenarioGraph,
        components: Record<string, ComponentDefinition>,
    ): VariableTypes => {
        const previousNodes = this.findPreviousNodes(nodeId, scenarioGraph);
        const variablesDefinedBeforeNodeList = previousNodes.flatMap((nodeId) => {
            return this.findVariablesDefinedInProcess(nodeId, scenarioGraph, components);
        });
        return this.listOfObjectsToObject(variablesDefinedBeforeNodeList);
    };

    private listOfObjectsToObject = <T>(list: Record<string, T>[]): Record<string, T> => {
        return list.reduce((memo, current) => {
            return { ...memo, ...current };
        }, {});
    };

    private findVariablesDefinedInProcess = (
        nodeId: NodeId,
        scenarioGraph: ScenarioGraph,
        components: Record<string, ComponentDefinition>,
    ): Record<string, ReturnedType>[] => {
        const node = scenarioGraph.nodes.find((node) => node.id === nodeId);
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
                return isEmpty(clazzName) ? [] : [{ input: clazzName }];
            }
            case "FragmentInputDefinition": {
                return node.parameters?.map((param) => ({ [param.name]: param.typ }));
            }
            case "Enricher": {
                return [{ [node.output]: clazzName }];
            }
            case "CustomNode":
            case "Join": {
                return isEmpty(clazzName) ? [] : [{ [node.outputVar]: clazzName }];
            }
            case "VariableBuilder": {
                return [{ [node.varName]: unknown }];
            }
            case "Variable": {
                return [{ [node.varName]: unknown }];
            }
            case "Switch": {
                return node.exprVal ? [{ [node.exprVal]: unknown }] : [];
            }
            default: {
                return [];
            }
        }
    };

    // It should be synchronized with ComponentInfoExtractor.fromScenarioNode
    private determineComponentType = (node?: NodeType): string | null => {
        switch (node?.type) {
            case "Source":
                return "source";
            case "Sink":
                return "sink";
            case "Enricher":
            case "Processor":
                return "service";
            case "Join":
            case "CustomNode":
                return "custom";
            case "FragmentInput":
                return "fragment";
            case "Filter":
            case "Split":
            case "Switch":
            case "Variable":
            case "VariableBuilder":
            case "FragmentInputDefinition":
            case "FragmentOutputDefinition":
                return "builtin";
            default:
                return null;
        }
    };

    // It should be synchronized with ComponentInfoExtractor.fromScenarioNode
    private determineComponentName = (node: NodeType): string | null => {
        switch (node?.type) {
            case "Source":
            case "Sink": {
                return node.ref.typ;
            }
            case "FragmentInput": {
                return node.ref.id;
            }
            case "Enricher":
            case "Processor": {
                return node.service.id;
            }
            case "Join":
            case "CustomNode": {
                return node.nodeType;
            }
            case "Filter": {
                return "filter";
            }
            case "Split": {
                return "split";
            }
            case "Switch": {
                return "choice";
            }
            case "Variable": {
                return "variable";
            }
            case "VariableBuilder": {
                return "record-variable";
            }
            case "FragmentInputDefinition": {
                return "input";
            }
            case "FragmentOutputDefinition": {
                return "output";
            }
            default: {
                return null;
            }
        }
    };

    private findPreviousNodes = (nodeId: NodeId, scenarioGraph: ScenarioGraph): NodeId[] => {
        const nodeEdge = scenarioGraph.edges?.find((edge) => edge.to === nodeId);
        if (isEmpty(nodeEdge)) {
            return [];
        } else {
            const previousNodes = this.findPreviousNodes(nodeEdge.from, scenarioGraph);
            return [nodeEdge.from].concat(previousNodes);
        }
    };
}

const processUtils = new ProcessUtils();

export default processUtils;
