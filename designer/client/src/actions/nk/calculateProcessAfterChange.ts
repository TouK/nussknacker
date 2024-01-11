import NodeUtils from "../../components/graph/NodeUtils";
import { fetchProcessDefinition } from "./processDefinitionData";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { mapProcessWithNewNode, replaceNodeOutputEdges } from "../../components/graph/utils/graphUtils";
import { alignFragmentWithSchema } from "../../components/graph/utils/fragmentSchemaAligner";
import { Edge, NodeType, ScenarioGraph, ProcessDefinitionData } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { Scenario } from "../../components/Process/types";

function alignFragmentsNodeWithSchema(scenario: Scenario, processDefinitionData: ProcessDefinitionData): ScenarioGraph {
    return {
        ...scenario.json,
        nodes: scenario.json.nodes.map((node) => {
            return node.type === "FragmentInput" ? alignFragmentWithSchema(processDefinitionData, node) : node;
        }),
    };
}

export function calculateProcessAfterChange(
    scenario: Scenario,
    before: NodeType,
    after: NodeType,
    outputEdges: Edge[],
): ThunkAction<Promise<ScenarioGraph>> {
    return async (dispatch, getState) => {
        if (NodeUtils.nodeIsProperties(after)) {
            const processDefinitionData = await dispatch(
                fetchProcessDefinition(scenario.processingType, scenario.json.properties.isFragment),
            );
            const processWithNewFragmentSchema = alignFragmentsNodeWithSchema(scenario, processDefinitionData);
            if (after?.length && after.id !== before.id) {
                dispatch({ type: "PROCESS_RENAME", name: after.id });
            }
            return { ...processWithNewFragmentSchema, ...after };
        }

        let changedProcess = scenario.json;
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
            changedProcess = replaceNodeOutputEdges(scenario.json, processDefinitionData, filtered, before.id);
        }

        return mapProcessWithNewNode(changedProcess, before, after);
    };
}
