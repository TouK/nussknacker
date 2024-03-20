import NodeUtils from "../../components/graph/NodeUtils";
import { fetchProcessDefinition } from "./processDefinitionData";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { mapProcessWithNewNode, replaceNodeOutputEdges } from "../../components/graph/utils/graphUtils";
import { alignFragmentWithSchema } from "../../components/graph/utils/fragmentSchemaAligner";
import { Edge, NodeType, ScenarioGraph, ProcessDefinitionData, ScenarioGraphWithName } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { Scenario } from "../../components/Process/types";

function alignFragmentsNodeWithSchema(scenarioGraph: ScenarioGraph, processDefinitionData: ProcessDefinitionData): ScenarioGraph {
    return {
        ...scenarioGraph,
        nodes: scenarioGraph.nodes.map((node) => {
            return node.type === "FragmentInput" ? alignFragmentWithSchema(processDefinitionData, node) : node;
        }),
    };
}

export function calculateProcessAfterChange(
    scenario: Scenario,
    before: NodeType,
    after: NodeType,
    outputEdges: Edge[],
): ThunkAction<Promise<ScenarioGraphWithName>> {
    return async (dispatch, getState) => {
        if (NodeUtils.nodeIsProperties(after)) {
            const processDefinitionData = await dispatch(fetchProcessDefinition(scenario.processingType, scenario.isFragment));
            const processWithNewFragmentSchema = alignFragmentsNodeWithSchema(scenario.scenarioGraph, processDefinitionData);
            // TODO: We shouldn't keep scenario name in properties.id - it is a top-level scenario property
            if (after.id !== before.id) {
                dispatch({ type: "PROCESS_RENAME", name: after.id });
            }

            const { id, ...properties } = after;

            return {
                processName: after.id,
                scenarioGraph: { ...processWithNewFragmentSchema, properties },
            };
        }

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
            processName: scenario.scenarioGraph.properties.id || scenario.name,
            scenarioGraph: mapProcessWithNewNode(changedProcess, before, after),
        };
    };
}
