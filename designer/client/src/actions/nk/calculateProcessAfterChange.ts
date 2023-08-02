import NodeUtils from "../../components/graph/NodeUtils";
import { fetchProcessDefinition } from "./processDefinitionData";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import { mapProcessWithNewNode, replaceNodeOutputEdges } from "../../components/graph/GraphUtils";
import { alignFragmentWithSchema } from "../../components/graph/FragmentSchemaAligner";
import { Edge, NodeType, Process } from "../../types";
import { ThunkAction } from "../reduxTypes";
import { NodeData } from "../../newTypes/displayableProcess";

function alignFragmentsNodeWithSchema(process, processDefinitionData) {
    return {
        ...process,
        nodes: process.nodes.map((node) => {
            return node.type === "FragmentInput" ? alignFragmentWithSchema(processDefinitionData, node) : node;
        }),
    };
}

export function calculateProcessAfterChange(
    process: Process,
    before: NodeType,
    after: NodeType,
    outputEdges: Edge[],
): ThunkAction<Promise<Process>> {
    return async (dispatch, getState) => {
        if (NodeUtils.nodeIsProperties(after)) {
            const processDef = await dispatch(fetchProcessDefinition(process.processingType, process.properties.isFragment));
            const processWithNewFragmentSchema = alignFragmentsNodeWithSchema(process, processDef.processDefinitionData);
            const { id, ...properties } = after;
            if (id?.length && id !== before.id) {
                dispatch({ type: "PROCESS_RENAME", name: id });
            }
            return { ...processWithNewFragmentSchema, properties };
        }

        let changedProcess = process;
        if (outputEdges) {
            const processDefinitionData = getProcessDefinitionData(getState());
            const filtered = outputEdges.map(({ to, ...e }) =>
                changedProcess.nodes.find((n) => n.id === to)
                    ? { ...e, to }
                    : {
                          ...e,
                          to: "" as NodeData["id"],
                      },
            );
            changedProcess = replaceNodeOutputEdges(process, processDefinitionData, filtered, before.id);
        }

        return mapProcessWithNewNode(changedProcess, before, after);
    };
}
