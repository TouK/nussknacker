import { get } from "lodash";

import type { NodeType, UIParameter } from "../../../types";
import { setImmutable } from "./setImmutable";

const parametersPath = (node) => {
    switch (node.type) {
        case "CustomNode":
        case "Join":
            return `parameters`;
        case "Source":
        case "Sink":
        case "FragmentInput":
            return `ref.parameters`;
        case "Enricher":
        case "Processor":
            return `service.parameters`;
        default:
            return null;
    }
};

//We want to change parameters in node based on current node definition. This function can be used in
//two cases: dynamic parameters handling and automatic node migrations (e.g. in fragments)
export function adjustParameters(node: Readonly<NodeType>, parameterDefinitions: Readonly<UIParameter[]>): NodeType {
    const path = parametersPath(node);

    if (!path || !parameterDefinitions) {
        return node;
    }

    const currentParameters = get(node, path);
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
    return setImmutable(node, path, adjustedParameters);
}
