import fp from "lodash/fp";
import { NodeType, ProcessDefinitionData } from "../../../types";

/*
 * TODO: It's a workaround
 * There may be situations where an open scenario has its fragment parameters edited by a different owner
 * In such cases, we need to align the fragmentInput properties with the updated state of the fragment process definition data.
 */
export function alignFragmentWithSchema(processDefinitionData: ProcessDefinitionData, fragmentNode: NodeType) {
    const fragmentId = fragmentNode.ref.id;
    const fragmentSchema = processDefinitionData.componentGroups
        // This is a workaround. We cannot look for the fragment schema only in the fragments group,
        // because fragmentInput can be moved to a different group.
        // In this case, there was an error, which is why we need to iterate through all groups.
        .flatMap((componentGroups) => componentGroups.components)
        .find((obj) => obj?.node?.ref?.id === fragmentId);
    const fragmentSchemaParameters = fragmentSchema.node.ref.parameters;
    const mergedParameters = fragmentSchemaParameters.map(
        (param) => fragmentNode.ref.parameters.find((nodeParam) => nodeParam.name === param.name) || param,
    );
    return fp.set("ref.parameters", mergedParameters, fragmentNode);
}
