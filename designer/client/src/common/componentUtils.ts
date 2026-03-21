// It should be synchronized with ComponentInfoExtractor.fromScenarioNode
import type { UIParameter } from "../types/definition";
import type { NodeType } from "../types/node";

function determineComponentType(node: NodeType) {
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
}

// It should be synchronized with ComponentInfoExtractor.fromScenarioNode
function determineComponentName(node: NodeType) {
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
}

export function determineComponentId(node: NodeType) {
    const componentType = determineComponentType(node);
    const componentName = determineComponentName(node);
    return componentType && componentName ? `${componentType}-${componentName}` : null;
}

function checkSuffix(string: string, suffixes: string[] = [], separator = "-") {
    return suffixes.some((suffix) => string === suffix || string.endsWith(`${separator}${suffix}`));
}

/** Check if enricher node should use DataMapper (OpenAPI or lookup enrichers) */
export function isDataMapper(node: NodeType, parameterDefinitions: UIParameter[]): boolean {
    switch (node.type) {
        case "Sink":
            return !parameterDefinitions.find((p) => p.typ.type === "Unknown");
        case "Enricher": {
            const kind = determineComponentName(node);
            return kind ? checkSuffix(kind, ["openAPI", "lookup"]) : false;
        }
    }
    return false;
}

/** Check if enricher node should use ConditionBuilder (decision-table enricher) */
export function isConditionBuilder(node: NodeType): boolean {
    switch (node.type) {
        case "Enricher": {
            const kind = determineComponentName(node);
            return kind ? checkSuffix(kind, ["decision-table"]) : false;
        }
    }
    return false;
}

/** Check if custom node is an aggregate (session, sliding, or tumbling) */
export function isAggregate(node: NodeType): boolean {
    return ["custom-aggregate-session", "custom-aggregate-sliding", "custom-aggregate-tumbling"].includes(determineComponentId(node));
}
