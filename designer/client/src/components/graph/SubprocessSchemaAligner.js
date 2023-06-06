import fp from "lodash/fp";

export function alignFragmentWithSchema(processDefinitionData, fragmentNode) {
    const fragmentId = fragmentNode.ref.id;
    const fragmentSchema = processDefinitionData.componentGroups
        .find((componentGroups) => {
            return componentGroups.name === "fragments";
        })
        .components.find((obj) => obj.node.ref.id === fragmentId);
    const fragmentSchemaParameters = fragmentSchema.node.ref.parameters;
    const mergedParameters = fragmentSchemaParameters.map(
        (param) => fragmentNode.ref.parameters.find((nodeParam) => nodeParam.name === param.name) || param,
    );
    return fp.set("ref.parameters", mergedParameters, fragmentNode);
}
