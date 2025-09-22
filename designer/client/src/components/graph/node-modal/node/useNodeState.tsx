import { identity, isEqual } from "lodash";
import type React from "react";
import { type SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { useDebounce } from "rooks";

import { editNode } from "../../../../actions/nk/editNode";
import { nodeValidationDynamicParametersLoaded } from "../../../../actions/nk/nodeDetails";
import { PendingPromise } from "../../../../common/PendingPromise";
import { useUserSettings } from "../../../../common/userSettings";
import { getScenario } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import type { EditedNode } from "../IdField";
import { applyIdFromFakeName } from "../IdField";
import type { NodeDetailsMeta } from "./NodeDetails";
import { useEditState } from "./useEditState";

export type EditState = "idle" | "processing" | "pending" | "error";
export type NodeState = {
    scenario: Scenario;
    node: NodeType;
    editedNode: EditedNode;
    outputEdges: Edge[];
    onChange: (node: React.SetStateAction<EditedNode>, edges?: React.SetStateAction<Edge[]>) => void;
    performNodeEdit: (editedNode: EditedNode, outputEdges: Edge[]) => Promise<void>;
    isTouched: boolean;
    editState: EditState;
    editStateRef: React.RefObject<EditState>;
};

export function getEdgesForNode(scenario: Scenario, node: NodeType) {
    return scenario.scenarioGraph.edges.filter(({ from }) => from === node.id);
}

const NODE_UPDATE_DEBOUNCE_TIMEOUT = 1500;

export function useNodeState(data: NodeDetailsMeta): NodeState {
    const dispatch = useAppDispatch();
    const [nodeId, setNodeId] = useState<string>(data.node.id);
    const [settings] = useUserSettings();
    const autoApply = settings["node.autoApply"];

    const scenarioFromGlobalStore = useAppSelector(getScenario);
    const nodeFromGlobalStore = useMemo(
        () => NodeUtils.getNodeById(nodeId, scenarioFromGlobalStore.scenarioGraph),
        [nodeId, scenarioFromGlobalStore.scenarioGraph],
    );

    const scenario = useMemo(() => scenarioFromGlobalStore || data.scenario, [data.scenario, scenarioFromGlobalStore]);
    const node = useMemo(() => nodeFromGlobalStore || data.node, [data.node, nodeFromGlobalStore]);

    const [editedNode, setEditedNode] = useState<EditedNode>(node);
    const [outputEdges, setOutputEdges] = useState<Edge[]>(() => getEdgesForNode(scenario, node));
    const [status, setStatus, editStateRef] = useEditState();

    const setEditedNodeWithDebounce = useDebounce((node) => {
        setEditedNode((currentNode) => (isEqual(currentNode, node) ? currentNode : node));
    }, NODE_UPDATE_DEBOUNCE_TIMEOUT);

    useEffect(() => {
        setEditedNodeWithDebounce.cancel();

        if (editStateRef.current === "processing") return;

        setNodeId(node.id);
        setEditedNodeWithDebounce(node);
    }, [editStateRef, node, setEditedNodeWithDebounce]);

    useEffect(() => {
        setOutputEdges((currentOutputEdges) => {
            const edgesForNode = getEdgesForNode(scenario, node);
            return isEqual(currentOutputEdges, edgesForNode) ? currentOutputEdges : edgesForNode;
        });
    }, [node, scenario]);

    const performNodeEdit = useCallback(
        async (editedNode: EditedNode, outputEdges: Edge[]) => {
            setStatus("processing");
            try {
                const after = applyIdFromFakeName(editedNode);
                // Webpack yield that awaits is unnecessary,
                // but in fact without this await,
                // we don't wait to editNode finish and the dialog is closed before resolve of the call,
                // which causes a bug with a form update
                await dispatch(editNode(scenario, node, after, outputEdges));
                if (autoApply) {
                    setNodeId(after.id);
                }
                setStatus("idle");
            } catch (e) {
                console.error(e);
                setStatus("error");
            } finally {
                if (autoApply) {
                    dispatch(nodeValidationDynamicParametersLoaded(node.id));
                }
            }
        },
        [setStatus, dispatch, scenario, node, autoApply],
    );
    const performNodeEditDebounced = useDebounce(performNodeEdit, NODE_UPDATE_DEBOUNCE_TIMEOUT);

    const isTouched = useMemo(() => node !== editedNode, [editedNode, node]);

    const onChange = useCallback(
        (nodeChange: SetStateAction<EditedNode>, edgesChange: SetStateAction<Edge[]> = identity) => {
            setEditedNodeWithDebounce.cancel();
            performNodeEditDebounced.cancel();

            const editedNode$ = new PendingPromise<[EditedNode, boolean]>();
            const outputEdges$ = new PendingPromise<[Edge[], boolean]>();

            setEditedNode((currentEditedNode) => {
                let nextEditedNode = typeof nodeChange === "function" ? nodeChange(currentEditedNode) : nodeChange;
                const equal = isEqual(currentEditedNode, nextEditedNode);
                if (equal) {
                    nextEditedNode = currentEditedNode;
                }
                editedNode$.resolve([nextEditedNode, !equal]);
                return nextEditedNode;
            });

            setOutputEdges((currentOutputEdges) => {
                let nextOutputEdges = typeof edgesChange === "function" ? edgesChange(currentOutputEdges) : edgesChange;
                const equal = isEqual(currentOutputEdges, nextOutputEdges);
                if (equal) {
                    nextOutputEdges = currentOutputEdges;
                }
                outputEdges$.resolve([nextOutputEdges, !equal]);
                return nextOutputEdges;
            });

            if (autoApply) {
                Promise.all([editedNode$, outputEdges$]).then(([[node, nodeChanged], [edges, edgesChanged]]) => {
                    if (!nodeChanged && !edgesChanged) return;

                    setStatus("pending");
                    performNodeEditDebounced(node, edges);
                });
            }
        },
        [autoApply, performNodeEditDebounced, setEditedNodeWithDebounce, setStatus],
    );

    return {
        scenario,
        node,
        editedNode,
        outputEdges,
        onChange,
        performNodeEdit,
        isTouched,
        editState: status,
        editStateRef,
    };
}
