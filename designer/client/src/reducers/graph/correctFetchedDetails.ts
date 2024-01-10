import { Edge, ScenarioGraph, ProcessDefinitionData } from "../../types";
import NodeUtils from "../../components/graph/NodeUtils";
import { Process } from "../../components/Process/types";

function getEdgeValidator(processToDisplay: ScenarioGraph, processDefinitionData?: ProcessDefinitionData) {
    return ({ from }: Edge): boolean => {
        return NodeUtils.hasOutputs(NodeUtils.getNodeById(from, processToDisplay), processDefinitionData);
    };
}

export function correctFetchedDetails(data: Process, definitionData?: ProcessDefinitionData): Process {
    const { json: scenarioGraph } = data;
    const { edges } = scenarioGraph;
    const isValidEdge = getEdgeValidator(scenarioGraph, definitionData);
    return {
        ...data,
        json: {
            ...scenarioGraph,
            edges: edges.filter(isValidEdge),
        },
    };
}
