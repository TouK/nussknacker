import { combine } from "kefir";
import { identity, isEqual } from "lodash";
import type React from "react";
import { type SetStateAction, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { editNode } from "../../../../actions/nk/editNode";
import { getScenario } from "../../../../reducers/selectors/graph";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import type { EditedNode } from "../nodeIdFieldHelpers";
import type { NodeDetailsMeta } from "./NodeDetails";
import { useCallbackRef } from "./useCallbackRef";
import { useEditState } from "./useEditState";
import { useStream } from "./useStream";

export type EditState = "idle" | "processing" | "pending" | "error";
export type NodeState = {
    scenario: Scenario;
    node: NodeType;
    editedNode: EditedNode;
    outputEdges: Edge[];
    onChange: (node: React.SetStateAction<EditedNode>, edges?: React.SetStateAction<Edge[]>) => void;
    performNodeEdit: (editedNode: EditedNode, outputEdges: Edge[]) => Promise<void>;
    editState: EditState;
    editStateRef: React.RefObject<EditState>;
};

export function getEdgesForNode(scenario: Scenario, node: NodeType) {
    return scenario.scenarioGraph.edges.filter(({ from }) => from === node.id);
}

export const NODE_UPDATE_DEBOUNCE_TIMEOUT = 500;

export function useNodeState(data: NodeDetailsMeta): NodeState {
    const dispatch = useAppDispatch();
    const settings = useAppSelector(getUserSettings);
    const autoApply = settings["node.autoApply"];

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

    const [editState, setStatus, editStateRef] = useEditState();

    const [node$, emitNode, editedNode] = useStream(node, true);
    const [edges$, emitEdges, outputEdges] = useStream(edges, true);

    const abortControllerRef = useRef<AbortController>(null);

    const [performNodeEditRef, performNodeEdit] = useCallbackRef(
        async (editedNode: EditedNode, outputEdges: Edge[]) => {
            const controller = new AbortController();
            abortControllerRef.current = controller;
            setStatus("processing");
            try {
                const after = await dispatch(editNode(scenario, node, editedNode, outputEdges, controller));
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
            return !isEqual(node, editedNode) || !isEqual(edges, outputEdges);
        },
        [edges, node],
    );

    const onChange = useCallback(
        (nodeChange: SetStateAction<EditedNode>, edgesChange: SetStateAction<Edge[]> = identity) => {
            emitNode(nodeChange);
            emitEdges(edgesChange);
        },
        [emitEdges, emitNode],
    );

    const change$ = useMemo(() => combine([node$, edges$]).map(([node, edges]) => ({ node, edges })), [edges$, node$]);

    useEffect(() => {
        if (!autoApply) return;
        const subscription = change$.observe(({ node, edges }) => {
            abortControllerRef.current?.abort();
            if (isTouchedRef.current(node, edges)) {
                setStatus("pending");
            } else {
                setStatus("idle");
            }
        });
        return subscription.unsubscribe;
    }, [autoApply, change$, isTouchedRef, setStatus]);

    useEffect(() => {
        if (!autoApply) return;
        const subscription = change$
            .debounce(NODE_UPDATE_DEBOUNCE_TIMEOUT)
            .skipDuplicates((a, b) => isEqual(a.node, b.node) && isEqual(a.edges, b.edges))
            .observe(({ node, edges }) => {
                performNodeEditRef.current(node, edges);
            });
        return subscription.unsubscribe;
    }, [autoApply, change$, performNodeEditRef]);

    return {
        scenario,
        node,
        editedNode,
        outputEdges,
        onChange,
        performNodeEdit,
        editState,
        editStateRef,
    };
}
