import { Edge, Process, ProcessDefinitionData } from "../../types";
import NodeUtils from "../../components/graph/NodeUtils";
import { ProcessType } from "../../components/Process/types";

function getEdgeValidator(processToDisplay: Process, processDefinitionData?: ProcessDefinitionData) {
    return ({ from }: Edge): boolean => {
        return NodeUtils.hasOutputs(NodeUtils.getNodeById(from, processToDisplay), processDefinitionData);
    };
}

export function correctFetchedDetails(data: ProcessType, definitionData?: ProcessDefinitionData): ProcessType {
    const { json: processToDisplay } = data;
    const { edges } = processToDisplay;
    const isValidEdge = getEdgeValidator(processToDisplay, definitionData);
    return {
        ...data,
        json: {
            ...processToDisplay,
            edges: edges.filter(isValidEdge),
        },
    };
}
