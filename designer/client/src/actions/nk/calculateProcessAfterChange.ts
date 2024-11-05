import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { mapProcessWithNewNode, replaceNodeOutputEdges } from "../../components/graph/utils/graphUtils";
import { Edge, NodeType, ScenarioGraphWithName } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { Scenario } from "../../components/Process/types";

export function calculateProcessAfterChange(
    scenario: Scenario,
    before: NodeType,
    after: NodeType,
    outputEdges: Edge[],
): ThunkAction<Promise<ScenarioGraphWithName>> {
    return async (_, getState) => {
        let changedProcess = scenario.scenarioGraph;
        if (outputEdges) {
            const processDefinitionData = getProcessDefinitionData(getState());
            const filtered = outputEdges.map(({ to, ...e }) =>
                changedProcess.nodes.find((n) => n.id === to)
                    ? { ...e, to }
                    : {
                          ...e,
                          to: "",
                      },
            );
            changedProcess = replaceNodeOutputEdges(scenario.scenarioGraph, processDefinitionData, filtered, before.id);
        }

        return {
            processName: scenario.name,
            scenarioGraph: mapProcessWithNewNode(changedProcess, before, after),
        };
    };
}
