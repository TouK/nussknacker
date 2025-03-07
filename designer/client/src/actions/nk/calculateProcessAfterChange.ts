import { cleanupNodeInputEdges, mapProcessWithNewNode, replaceNodeOutputEdges } from "../../components/graph/utils/graphUtils";
import { Scenario } from "../../components/Process/types";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { Edge, NodeType, ScenarioGraphWithName } from "../../types";
import { ThunkAction } from "../reduxTypes";

export function calculateProcessAfterChange(
    scenario: Scenario,
    before: NodeType,
    after: NodeType,
    outputEdges: Edge[],
): ThunkAction<Promise<ScenarioGraphWithName>> {
    return async (_, getState) => {
        let changedGraph = scenario.scenarioGraph;

        changedGraph = cleanupNodeInputEdges(changedGraph, before, after);
        if (outputEdges) {
            const processDefinitionData = getProcessDefinitionData(getState());
            const filtered = outputEdges.map(({ to, ...e }) =>
                changedGraph.nodes.find((n) => n.id === to)
                    ? { ...e, to }
                    : {
                          ...e,
                          to: "",
                      },
            );
            changedGraph = replaceNodeOutputEdges(changedGraph, processDefinitionData, filtered, before.id);
        }

        changedGraph = mapProcessWithNewNode(changedGraph, before, after);

        return {
            processName: scenario.name,
            scenarioGraph: changedGraph,
        };
    };
}
