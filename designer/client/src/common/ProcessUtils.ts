/* eslint-disable i18next/no-literal-string */
import { flatten, isEmpty, isEqual, map, omit, pickBy, transform } from "lodash";
import {
    ComponentDefinition,
    NodeId,
    NodeResults,
    NodeType,
    Process,
    ReturnedType,
    TypingResult,
    UIParameter,
    ValidationResult,
    VariableTypes,
} from "../types";
import { RootState } from "../reducers";
import { isProcessRenamed } from "../reducers/selectors/graph";

class ProcessUtils {
    nothingToSave = (state: RootState): boolean => {
        const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails;
        const processToDisplay = state.graphReducer.processToDisplay;
        //TODO: validationResult should be removed from processToDisplay...
        const omitValidation = (details: Process) => omit(details, ["validationResult"]);
        const processRenamed = isProcessRenamed(state);

        if (processRenamed) {
            return false;
        }

        if (isEmpty(fetchedProcessDetails)) {
            return true;
        }

        return isEqual(omitValidation(fetchedProcessDetails.json), omitValidation(processToDisplay));
    };

    canExport = (state: RootState): boolean => {
        const fetchedProcessDetails = state.graphReducer.fetchedProcessDetails;
        return isEmpty(fetchedProcessDetails) ? false : !isEmpty(fetchedProcessDetails.json.nodes);
    };

    //fixme maybe return hasErrors flag from backend?
    hasNeitherErrorsNorWarnings = (process: Process) => {
        return this.hasNoErrors(process) && this.hasNoWarnings(process);
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

    hasNoErrors = (process: Process) => {
        const result = this.getValidationErrors(process);
        return (
            !result ||
            (Object.keys(result.invalidNodes || {}).length == 0 &&
                (result.globalErrors || []).length == 0 &&
                (result.processPropertiesErrors || []).length == 0)
        );
    };

    getValidationResult = (process: Process): ValidationResult =>
        process?.validationResult || { validationErrors: [], validationWarnings: [], nodeResults: {} };

    hasNoWarnings = (process: Process) => {
        const warnings = this.getValidationResult(process).warnings;
        return isEmpty(warnings) || Object.keys(warnings.invalidNodes || {}).length == 0;
    };

    hasNoPropertiesErrors = (process: Process) => {
        return isEmpty(this.getValidationErrors(process)?.processPropertiesErrors);
    };

    getValidationErrors(process: Process) {
        return this.getValidationResult(process).errors;
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

    getNodeResults = (process: Process): NodeResults => this.getValidationResult(process).nodeResults;

    //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions#Escaping
    escapeNodeIdForRegexp = (id: string) => id && id.replace(/[.*+\-?^${}()|[\]\\]/g, "\\$&");

    findAvailableVariables =
        (components: Record<string, ComponentDefinition>, process: Process) =>
        (nodeId: NodeId, parameterDefinition?: UIParameter): VariableTypes => {
            const nodeResults = this.getNodeResults(process);
            const variablesFromValidation = this.getVariablesFromValidation(nodeResults, nodeId);
            const variablesForNode = variablesFromValidation || this._findVariablesDeclaredBeforeNode(nodeId, process, components);
            const variablesToHideForParam = parameterDefinition?.variablesToHide || [];
            const withoutVariablesToHide = pickBy(variablesForNode, (va, key) => !variablesToHideForParam.includes(key));
            const additionalVariablesForParam = parameterDefinition?.additionalVariables || {};
            return { ...withoutVariablesToHide, ...additionalVariablesForParam };
        };

    getVariablesFromValidation = (nodeResults: NodeResults, nodeId: string) => nodeResults?.[nodeId]?.variableTypes;

    _findVariablesDeclaredBeforeNode = (
        nodeId: NodeId,
        process: Process,
        components: Record<string, ComponentDefinition>,
    ): VariableTypes => {
        const previousNodes = this._findPreviousNodes(nodeId, process);
        const variablesDefinedBeforeNodeList = previousNodes.flatMap((nodeId) => {
            return this._findVariablesDefinedInProcess(nodeId, process, components);
        });
        return this._listOfObjectsToObject(variablesDefinedBeforeNodeList);
    };

    _listOfObjectsToObject = <T>(list: Record<string, T>[]): Record<string, T> => {
        return list.reduce((memo, current) => {
            return { ...memo, ...current };
        }, {});
    };

    _findVariablesDefinedInProcess = (
        nodeId: NodeId,
        process: Process,
        components: Record<string, ComponentDefinition>,
    ): Record<string, ReturnedType>[] => {
        const node = process.nodes.find((node) => node.id === nodeId);
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

    extractComponentDefinition = (node: NodeType, components: Record<string, ComponentDefinition>): ComponentDefinition | null => {
        return components?.[this.determineComponentId(node)];
    };

    determineComponentId = (node?: NodeType): string | null => {
        const componentType = this.determineComponentType(node);
        const componentName = this.determineComponentName(node);
        return (componentType && componentName && componentType + "-" + componentName) || null;
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

    humanReadableType = (typingResult?: Pick<TypingResult, "display">): string | null => typingResult?.display || null;

    _findPreviousNodes = (nodeId: NodeId, process: Process): NodeId[] => {
        const nodeEdge = process.edges.find((edge) => edge.to === nodeId);
        if (isEmpty(nodeEdge)) {
            return [];
        } else {
            const previousNodes = this._findPreviousNodes(nodeEdge.from, process);
            return [nodeEdge.from].concat(previousNodes);
        }
    };
}

export default new ProcessUtils();
