// It should be synchronized with ComponentInfoExtractor.fromScenarioNode
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
