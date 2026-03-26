import { combine } from "kefir";
import { identity, isEqual } from "lodash";
import type React from "react";
import { type SetStateAction, useCallback, useEffect, useId, useMemo, useState } from "react";

import { editNode } from "../../../../actions/nk/editNode";
import { useRemoteApply } from "../../../../actions/nk/useRemoteApply";
import { getScenario } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import type { NodeDetailsMeta } from "./NodeDetails";
import { useCallbackRef } from "./useCallbackRef";
import { useEditState } from "./useEditState";
import { useStream } from "./useStream";

export type EditState = "idle" | "processing" | "pending" | "error";
export type NodeState = {
    node: NodeType;
    editedNode: NodeType;
    outputEdges: Edge[];
    onChange: (node: React.SetStateAction<NodeType>, edges?: React.SetStateAction<Edge[]>) => void;
    performNodeEdit: (editedNode: NodeType, outputEdges: Edge[]) => Promise<void>;
    editState: EditState;
};

export function getEdgesForNode(scenario: Scenario, node: NodeType) {
    return scenario.scenarioGraph.edges.filter(({ from }) => from === node.id);
}

export function useNodeState(data: NodeDetailsMeta): NodeState {
    const dispatch = useAppDispatch();

    const openedNode = data?.node;
    const [nodeId, setNodeId] = useState<string>(openedNode?.id);

    const scenarioFromGlobalStore = useAppSelector(getScenario);

    const scenario = useMemo(() => scenarioFromGlobalStore || data.scenario, [data.scenario, scenarioFromGlobalStore]);
    const [node, setNode] = useState(openedNode);
    useEffect(
        () =>
            setNode((current) => {
                const nextNode = NodeUtils.getNodeById(nodeId, scenarioFromGlobalStore.scenarioGraph) || current;
                return isEqual(nextNode, current) ? current : nextNode;
            }),
        [nodeId, scenarioFromGlobalStore.scenarioGraph],
    );

    const edges = useMemo(() => getEdgesForNode(scenario, node), [node, scenario]);

    const key: string = useId();
    const [editState, setStatus] = useEditState(key);

    const [node$, emitNode, editedNode] = useStream(node, true);
    const [edges$, emitEdges, outputEdges] = useStream(edges, true);

    const [performNodeEditRef, performNodeEdit] = useCallbackRef(
        async (editedNode: NodeType, outputEdges: Edge[]) => {
            setStatus("processing");
            try {
                const after = await dispatch(editNode(scenario, node, editedNode, outputEdges));
                setNodeId(after.id);
                setStatus("idle");
            } catch (e) {
                setStatus("error");
                return Promise.reject(e);
            }
        },
        [dispatch, node, scenario, setStatus],
    );

    const [isTouchedRef] = useCallbackRef(
        (editedNode, outputEdges) => {
            const nodeEqual = !editedNode || isEqual(node, editedNode);
            const edgesEqual = !outputEdges || isEqual(edges, outputEdges);
            return !(nodeEqual && edgesEqual);
        },
        [edges, node],
    );

    const onChange = useCallback(
        (nodeChange: SetStateAction<NodeType>, edgesChange: SetStateAction<Edge[]> = identity) => {
            emitNode(nodeChange);
            emitEdges(edgesChange);
        },
        [emitEdges, emitNode],
    );

    const change$ = useMemo(() => combine([node$, edges$]).map(([node, edges]) => ({ node, edges })), [edges$, node$]);

    useEffect(() => {
        const subscription = change$.observe(({ node, edges }) => {
            if (isTouchedRef.current(node, edges)) {
                setStatus("pending");
            } else {
                setStatus("idle");
            }
        });
        return subscription.unsubscribe;
    }, [change$, isTouchedRef, setStatus]);

    useRemoteApply(key, () => performNodeEditRef.current(editedNode, outputEdges));

    return {
        node,
        editedNode,
        outputEdges,
        onChange,
        performNodeEdit,
        editState,
    };
}
