import { Edge, ScenarioGraph } from "../../../types";

export function isEdgeConnected(edge: Edge): boolean {
    return !!edge.from && !!edge.to;
}

//TODO: handle "piggybacking" of edges with different mechanism than empty edges
export function withoutHackOfEmptyEdges(scenarioGraph: ScenarioGraph): ScenarioGraph {
    return {
        ...scenarioGraph,
        edges: scenarioGraph.edges?.filter(isEdgeConnected),
    };
}
