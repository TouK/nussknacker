import { cloneDeep, get, set } from "lodash";

import type { NodeType, UIParameter } from "../../../types";

const parametersPath = (node) => {
    switch (node.type) {
        case "CustomNode":
            return `parameters`;
        case "Join":
            return `parameters`;
        case "Source":
        case "Sink":
        case "FragmentInput":
            return `ref.parameters`;
        case "Enricher":
            return `service.parameters`;
        case "Processor":
            return `service.parameters`;
        default:
            return null;
    }
};

//We want to change parameters in node based on current node definition. This function can be used in
//two cases: dynamic parameters handling and automatic node migrations (e.g. in fragments)
export function adjustParameters(node: NodeType, parameterDefinitions: UIParameter[]): NodeType {
    const path = parametersPath(node);

    if (!path || !parameterDefinitions) {
        return node;
    }

    const currentNode = cloneDeep(node);
    const currentParameters = get(currentNode, path);
    //TODO: currently dynamic branch parameters are *not* supported...
    const adjustedParameters = parameterDefinitions
        .filter((def) => !def.branchParam)
        .map((def) => {
            const currentParam = currentParameters.find((p) => p.name == def.name);
            const parameterFromDefinition = {
                name: def.name,
                expression: def.defaultValue,
            };
            return currentParam || parameterFromDefinition;
        });
    return set(currentNode, path, adjustedParameters);
}
