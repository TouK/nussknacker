import NodeUtils from "../../components/graph/NodeUtils";
import type { Scenario } from "../../components/Process/types";
import type { Edge } from "../../types/edge";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";

function getOutputEdgeValidator(scenarioGraph: ScenarioGraph, processDefinitionData: ProcessDefinitionData) {
    return ({ from }: Edge) => NodeUtils.hasOutputs(NodeUtils.getNodeById(from, scenarioGraph), processDefinitionData);
}

function getInputEdgeValidator(scenarioGraph: ScenarioGraph) {
    return ({ to }: Edge) => NodeUtils.hasInputs(NodeUtils.getNodeById(to, scenarioGraph));
}

// TODO: we could not only check if hasOutput for from, but also check if Edge.edgeType.name matches
//       available edges(name) from the definition
function getEdgesCorrector(scenarioGraph: ScenarioGraph, definitionData: ProcessDefinitionData) {
    const outputEdgeValidator = getOutputEdgeValidator(scenarioGraph, definitionData);
    const inputEdgeValidator = getInputEdgeValidator(scenarioGraph);
    return (edges: Edge[]) =>
        edges
            .map((e: Edge) => {
                if (!outputEdgeValidator(e)) return null;
                if (!inputEdgeValidator(e)) return null;
                return e;
            })
            .filter(Boolean);
}

// TODO: This should be one on the BE side
export function correctFetchedDetails(data: Scenario, definitionData?: ProcessDefinitionData): Scenario {
    const { scenarioGraph } = data;
    const edgesCorrector = getEdgesCorrector(scenarioGraph, definitionData);
    return {
        ...data,
        scenarioGraph: {
            ...scenarioGraph,
            edges: edgesCorrector(scenarioGraph.edges),
        },
    };
}
