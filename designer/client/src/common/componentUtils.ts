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

// Multi-word kinds that can't be extracted by simple last-segment split
const MULTI_WORD_KINDS = ["decision-table", "flink-sql-query"];

/** Enricher kinds that get the node-level OpenAPI-style DataMapper (named params, no schema loading) */
export const DATA_MAPPER_OPENAPI_ENRICHER_KINDS = new Set(["openAPI", "lookup"]);

/** Enricher kinds that get the node-level ConditionBuilder */
export const CONDITION_BUILDER_ENRICHER_KINDS = new Set(["decision-table"]);

/** Extracts the component kind from a componentId, e.g.:
 *  sink-kafka            → "kafka"
 *  service-petstore-openAPI → "openAPI"
 *  service-aiven-postgres-lookup → "lookup"
 *  service-decision-table → "decision-table"  (multi-word exception)
 *  custom-flink-sql-query → "flink-sql-query" (multi-word exception)
 */
export function getComponentKind(componentId: string | null): string | null {
    if (!componentId) return null;
    for (const kind of MULTI_WORD_KINDS) {
        if (componentId.endsWith(`-${kind}`)) return kind;
    }
    const lastDash = componentId.lastIndexOf("-");
    return lastDash >= 0 ? componentId.slice(lastDash + 1) : componentId;
}
