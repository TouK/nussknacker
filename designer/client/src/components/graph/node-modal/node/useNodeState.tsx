import type { SetStateAction } from "react";
import type React from "react";
import { useCallback, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import { editNode } from "../../../../actions/nk";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../../../containers/hooks/useSearchQuery";
import { getScenario } from "../../../../reducers/selectors/graph";
import type { Edge, NodeType } from "../../../../types";
import type { Scenario } from "../../../Process/types";
import type { EditedNode } from "../IdField";
import { applyIdFromFakeName } from "../IdField";
import type { NodeDetailsMeta } from "./NodeDetails";

export function mergeQuery(changes: Record<string, string[]>) {
    return replaceSearchQuery((current) => ({ ...current, ...changes }));
}

type NodeState = {
    scenario: Scenario;
    node: NodeType;
    editedNode: EditedNode;
    outputEdges: Edge[];
    onChange: (node: React.SetStateAction<EditedNode>, edges?: React.SetStateAction<Edge[]>) => void;
    performNodeEdit: () => Promise<void>;
    isTouched: boolean;
};

export function getEdgesForNode(scenario: Scenario, node: NodeType) {
    return scenario.scenarioGraph.edges.filter(({ from }) => from === node.id);
}

export function useNodeState(data: NodeDetailsMeta): NodeState {
    const dispatch = useDispatch();
    const scenarioFromGlobalStore = useSelector(getScenario);

    const { node, scenario = scenarioFromGlobalStore } = data;
    const [editedNode, setEditedNode] = useState<EditedNode>(node);
    const [outputEdges, setOutputEdges] = useState<Edge[]>(() => getEdgesForNode(scenario, node));

    const onChange = useCallback((node: SetStateAction<EditedNode>, edges: SetStateAction<Edge[]> = (v) => v) => {
        setEditedNode(node);
        setOutputEdges(edges);
    }, []);

    const isTouched = useMemo(() => node !== editedNode, [editedNode, node]);

    const performNodeEdit = useCallback(async () => {
        try {
            //TODO: without removing nodeId query param, the dialog after close, is opening again. It looks like useModalDetailsIfNeeded is fired after edit, because nodeId is still in the query string params, after scenario changes.
            mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));

            // Webpack yield that awaits is unnecessary,
            // but in fact without this await,
            // we don't wait to editNode finish and the dialog is closed before resolve of the call,
            // which causes a bug with a form update
            await dispatch(editNode(scenario, node, applyIdFromFakeName(editedNode), outputEdges));
        } catch (e) {
            console.error(e);
            //TODO: It's a workaround and continuation of above TODO, let's revert query param deletion, if dialog is still open because of server error
            mergeQuery(parseWindowsQueryParams({ nodeId: node.id }, {}));
        }
    }, [node, dispatch, scenario, editedNode, outputEdges]);

    return {
        scenario,
        node,
        editedNode,
        outputEdges,
        onChange,
        performNodeEdit,
        isTouched,
    };
}
