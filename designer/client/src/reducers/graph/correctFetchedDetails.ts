import { Edge, ScenarioGraph, ProcessDefinitionData } from "../../types";
import NodeUtils from "../../components/graph/NodeUtils";
import { Scenario } from "../../components/Process/types";

function getEdgeValidator(scenarioGraph: ScenarioGraph, processDefinitionData?: ProcessDefinitionData) {
    return ({ from }: Edge): boolean => {
        return NodeUtils.hasOutputs(NodeUtils.getNodeById(from, scenarioGraph), processDefinitionData);
    };
}

export function correctFetchedDetails(data: Scenario, definitionData?: ProcessDefinitionData): Scenario {
    const { scenarioGraph: scenarioGraph } = data;
    const { edges } = scenarioGraph;
    const isValidEdge = getEdgeValidator(scenarioGraph, definitionData);
    return {
        ...data,
        scenarioGraph: {
            ...scenarioGraph,
            edges: edges.filter(isValidEdge),
        },
    };
}
