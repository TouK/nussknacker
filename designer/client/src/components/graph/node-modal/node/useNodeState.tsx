import { combine } from "kefir";
import { identity, isEqual } from "lodash";
import type React from "react";
import { type SetStateAction, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { editNode } from "../../../../actions/nk/editNode";
import { nodeValidationDynamicParametersLoaded } from "../../../../actions/nk/nodeDetails";
import { getScenario } from "../../../../reducers/selectors/graph";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import type { EditedNode } from "../IdField";
import { applyIdFromFakeName } from "../IdField";
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

    const [nodeId, setNodeId] = useState<string>(data.node.id);

    const scenarioFromGlobalStore = useAppSelector(getScenario);
    const nodeFromGlobalStore = useMemo(
        () => NodeUtils.getNodeById(nodeId, scenarioFromGlobalStore.scenarioGraph),
        [nodeId, scenarioFromGlobalStore.scenarioGraph],
    );

    const scenario = useMemo(() => scenarioFromGlobalStore || data.scenario, [data.scenario, scenarioFromGlobalStore]);
    const node = useMemo(() => nodeFromGlobalStore || data.node, [data.node, nodeFromGlobalStore]);
    const edges = useMemo(() => getEdgesForNode(scenario, node), [node, scenario]);

    const [status, setStatus, editStateRef] = useEditState();

    const [node$, emitNode, editedNode] = useStream(node, true);
    const [edges$, emitEdges, outputEdges] = useStream(edges, true);

    const abortControllerRef = useRef<AbortController>(null);

    const [performNodeEditRef, performNodeEdit] = useCallbackRef(
        async (editedNode: EditedNode, outputEdges: Edge[]) => {
            const controller = new AbortController();
            abortControllerRef.current = controller;

            setStatus("processing");
            try {
                const after = applyIdFromFakeName(editedNode);
                await dispatch(editNode(scenario, node, after, outputEdges, controller));
                if (autoApply) {
                    setNodeId(after.id);
                }
                setStatus("idle");
            } catch (e) {
                console.error(e);
                setStatus("error");
            } finally {
                if (!controller.signal.aborted && autoApply) {
                    dispatch(nodeValidationDynamicParametersLoaded(node.id));
                }
            }
        },
        [autoApply, dispatch, node, scenario, setStatus],
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
        editState: status,
        editStateRef,
    };
}
