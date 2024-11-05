import NodeUtils from "../NodeUtils";
import { NodeType, Parameter, UIParameter } from "../../../types";
import { cloneDeep, get, set } from "lodash";

export type AdjustReturn = {
    adjustedNode: NodeType;
    //currently not used, but maybe we can e.g. display them somewhere?
    unusedParameters: Parameter[];
};

const findUnusedParameters = (parameters: Array<Parameter>, definitions: UIParameter[] = []) => {
    return parameters.filter((param) => !definitions.find((def) => def.name == param.name));
};

const parametersPath = (node) => {
    switch (node.nodeType) {
        case "CustomNode":
            return `parameters`;
        case "Join":
            return `parameters`;
        case "Source":
        case "Sink":
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
//two cases: dynamic parameters handling and automatic node migrations (e.g. in fragments). Currently we use it only for dynamic parameters
export function adjustParameters(node: NodeType, parameterDefinitions: UIParameter[]): AdjustReturn {
    const path = parametersPath(node);

    if (!path) {
        return { adjustedNode: node, unusedParameters: [] };
    }

    const currentNode = cloneDeep(node);
    const currentParameters = get(currentNode, path);
    //TODO: currently dynamic branch parameters are *not* supported...
    const adjustedParameters = parameterDefinitions
        ?.filter((def) => !def.branchParam)
        .map((def) => {
            const currentParam = currentParameters.find((p) => p.name == def.name);
            const parameterFromDefinition = {
                name: def.name,
                expression: def.defaultValue,
            };
            return currentParam || parameterFromDefinition;
        });
    const adjustedNode = set(currentNode, path, adjustedParameters);
    const unusedParameters = findUnusedParameters(currentParameters, parameterDefinitions);
    return { adjustedNode, unusedParameters };
}
