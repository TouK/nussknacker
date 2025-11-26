import { produce } from "immer";

import {
    cleanProperties,
    isRequestSource,
    nodePropertiesToScenarioProperties,
} from "../../components/graph/node-modal/requestSourceAddons";
import { cleanupNodeInputEdges, mapProcessWithNewNode, replaceNodeOutputEdges } from "../../components/graph/utils/graphUtils";
import type { Scenario } from "../../components/Process/types";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import type { Edge } from "../../types/edge";
import type { NodeType } from "../../types/node";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { ThunkAction } from "../reduxTypes";

// FIXME: this is not an action,
export function calculateProcessAfterChange(
    scenario: Scenario,
    before: NodeType,
    _after: NodeType,
    outputEdges: Edge[],
): ThunkAction<Promise<ScenarioGraph>> {
    return async (_, getState) => {
        let changedGraph = scenario.scenarioGraph;
        let after = _after;

        if (isRequestSource(after)) {
            changedGraph = produce(changedGraph, (scenarioGraph) => {
                scenarioGraph.properties.additionalFields.properties = {
                    ...scenarioGraph.properties.additionalFields.properties,
                    ...nodePropertiesToScenarioProperties(after),
                };
            });
            after = cleanProperties(after);
        }

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

        return changedGraph;
    };
}
