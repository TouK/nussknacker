import { get } from "lodash";

import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { ProcessAdditionalFields } from "../../../types/scenarioGraph";
import { isRequestSource, scenarioPropertiesToNodeProperties } from "./requestSourceAddons";
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
export function adjustParameters(
    node: Readonly<NodeType>,
    parameterDefinitions: Readonly<UIParameter[]>,
    properties: ProcessAdditionalFields["properties"],
): NodeType {
    const path = parametersPath(node);

    if (!path || !parameterDefinitions) {
        return node;
    }

    let currentParameters;
    currentParameters = get(node, path);
    if (isRequestSource(node)) {
        currentParameters = currentParameters.concat(scenarioPropertiesToNodeProperties(properties));
    }

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
