import { identity, isEqual } from "lodash";
import type React from "react";
import { type SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useDebounce } from "rooks";

import { editNode } from "../../../../actions/nk";
import { PendingPromise } from "../../../../common/PendingPromise";
import { useUserSettings } from "../../../../common/userSettings";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../../../containers/hooks/useSearchQuery";
import { getScenario } from "../../../../reducers/selectors/graph";
import type { Edge, NodeType } from "../../../../types";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import type { EditedNode } from "../IdField";
import { applyIdFromFakeName } from "../IdField";
import type { NodeDetailsMeta } from "./NodeDetails";
import { useEditState } from "./useEditState";

export function mergeQuery(changes: Record<string, string[]>) {
    return replaceSearchQuery((current) => ({ ...current, ...changes }));
}

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
};

export function getEdgesForNode(scenario: Scenario, node: NodeType) {
    return scenario.scenarioGraph.edges.filter(({ from }) => from === node.id);
}

export function useNodeState(data: NodeDetailsMeta): NodeState {
    const dispatch = useDispatch();
    const [nodeId, setNodeId] = useState<string>(data.node.id);
    const [settings] = useUserSettings();
    const autoApply = settings["node.autoApply"];

    const scenarioFromGlobalStore = useSelector(getScenario);
    const nodeFromGlobalStore = useMemo(
        () => NodeUtils.getNodeById(nodeId, scenarioFromGlobalStore.scenarioGraph),
        [nodeId, scenarioFromGlobalStore.scenarioGraph],
    );

    const scenario = useMemo(() => scenarioFromGlobalStore || data.scenario, [data.scenario, scenarioFromGlobalStore]);
    const node = useMemo(() => nodeFromGlobalStore || data.node, [data.node, nodeFromGlobalStore]);

    const [editedNode, setEditedNode] = useState<EditedNode>(node);
    const [outputEdges, setOutputEdges] = useState<Edge[]>(() => getEdgesForNode(scenario, node));

    useEffect(() => {
        setEditedNode((currentNode) => (isEqual(currentNode, node) ? currentNode : node));
        setNodeId(node.id);
    }, [node]);

    useEffect(() => {
        mergeQuery(parseWindowsQueryParams({ nodeId: nodeId }));
        return () => {
            mergeQuery(parseWindowsQueryParams({}, { nodeId: nodeId }));
        };
    }, [nodeId]);

    useEffect(() => {
        setOutputEdges((currentOutputEdges) => {
            const edgesForNode = getEdgesForNode(scenario, node);
            return isEqual(currentOutputEdges, edgesForNode) ? currentOutputEdges : edgesForNode;
        });
    }, [node, scenario]);

    const [status, setStatus] = useEditState();
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
            }
        },
        [dispatch, scenario, node, autoApply, setStatus],
    );
    const performNodeEditDebounced = useDebounce(performNodeEdit, 750);

    const isTouched = useMemo(() => node !== editedNode, [editedNode, node]);

    const onChange = useCallback(
        (nodeChange: SetStateAction<EditedNode>, edgesChange: SetStateAction<Edge[]> = identity) => {
            const editedNode$ = new PendingPromise<[EditedNode, boolean]>();
            const outputEdges$ = new PendingPromise<[Edge[], boolean]>();

            if (nodeChange !== identity) {
                setEditedNode((currentEditedNode) => {
                    let nextEditedNode = typeof nodeChange === "function" ? nodeChange(currentEditedNode) : nodeChange;
                    const equal = isEqual(currentEditedNode, nextEditedNode);
                    if (equal) {
                        nextEditedNode = currentEditedNode;
                    }
                    editedNode$.resolve([nextEditedNode, !equal]);
                    return nextEditedNode;
                });
            }

            if (edgesChange !== identity) {
                setOutputEdges((currentOutputEdges) => {
                    let nextOutputEdges = typeof edgesChange === "function" ? edgesChange(currentOutputEdges) : edgesChange;
                    const equal = isEqual(currentOutputEdges, nextOutputEdges);
                    if (equal) {
                        nextOutputEdges = currentOutputEdges;
                    }
                    outputEdges$.resolve([nextOutputEdges, !equal]);
                    return nextOutputEdges;
                });
            }

            Promise.all([editedNode$, outputEdges$]).then(([[node, nodeChanged], [edges, edgesChanged]]) => {
                if (!autoApply) return;
                if (!nodeChanged && !edgesChanged) return;

                setStatus("pending");
                performNodeEditDebounced(node, edges);
            });
        },
        [autoApply, performNodeEditDebounced, setStatus],
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
    };
}
