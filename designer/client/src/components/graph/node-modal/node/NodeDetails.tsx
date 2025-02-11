import { css } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import urljoin from "url-join";
import { editNode } from "../../../../actions/nk";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { BASE_PATH } from "../../../../config";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../../../containers/hooks/useSearchQuery";
import { RootState } from "../../../../reducers";
import { getScenario, getScenarioGraph, isValidationResultPresent } from "../../../../reducers/selectors/graph";
import { Edge, NodeType } from "../../../../types";
import { WindowContent, WindowKind } from "../../../../windowManager";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import { applyIdFromFakeName } from "../IdField";
import { getNodeDetailsModalTitle, NodeDetailsModalIcon, NodeDetailsModalSubheader } from "../nodeDetails/NodeDetailsModalHeader";
import { NodeGroupContent } from "./NodeGroupContent";
import { getReadOnly } from "./selectors";
import { Box, CircularProgress } from "@mui/material";

function mergeQuery(changes: Record<string, string[]>) {
    return replaceSearchQuery((current) => ({ ...current, ...changes }));
}

type NodeDetailsMeta = { node: NodeType; scenario: Scenario };
type NodeDetailsProps = WindowContentProps<WindowKind, NodeDetailsMeta> & {
    readOnly?: boolean;
};

type NodeState = {
    scenario: Scenario;
    node: NodeType;
    editedNode: NodeType;
    outputEdges: Edge[];
    onChange: (node: React.SetStateAction<NodeType>, edges?: React.SetStateAction<Edge[]>) => void;
    performNodeEdit: () => Promise<void>;
    isTouched: boolean;
};

export function useNodeState(data: NodeDetailsMeta): NodeState {
    const dispatch = useDispatch();
    const scenarioFromGlobalStore = useSelector(getScenario);

    const { node, scenario = scenarioFromGlobalStore } = data;
    const [editedNode, setEditedNode] = useState<NodeType>(node);
    const [outputEdges, setOutputEdges] = useState<Edge[]>(() => scenario.scenarioGraph.edges.filter(({ from }) => from === node.id));

    const onChange = useCallback((node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]> = (v) => v) => {
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

export function useNodeDetailsButtons({
    editedNode,
    performNodeEdit,
    close,
    readOnly,
}: {
    editedNode: NodeType;
    performNodeEdit: () => Promise<void>;
    close: () => void;
    readOnly?: boolean;
}) {
    const { t } = useTranslation();

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (readOnly) return false;
        return {
            title: t("dialog.button.apply", "apply"),
            action: () => performNodeEdit().then(() => close()),
            disabled: !editedNode.id?.length,
        };
    }, [close, editedNode.id?.length, performNodeEdit, readOnly, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.cancel", "cancel"),
            action: () => close(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [close, t]);

    return { apply, cancel };
}

function NodeDetails(props: NodeDetailsProps): JSX.Element {
    const { t } = useTranslation();
    const { close, data } = props;
    const readOnly = useSelector((s: RootState) => getReadOnly(s, props.readOnly));
    const validationResultPresent = useSelector(isValidationResultPresent);
    const scenarioGraph = useSelector(getScenarioGraph);

    const { node, editedNode, onChange, scenario, outputEdges, performNodeEdit } = useNodeState(data.meta);
    const { cancel, apply } = useNodeDetailsButtons({ editedNode, performNodeEdit, close, readOnly });

    useEffect(() => {
        mergeQuery(parseWindowsQueryParams({ nodeId: node.id }));
        return () => {
            mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));
        };
    }, [node.id]);

    const openFragment = useMemo<WindowButtonProps | false>(() => {
        if (!NodeUtils.nodeIsFragment(editedNode)) return false;
        return {
            title: t("dialog.button.fragment.edit", "edit fragment"),
            action: () => {
                window.open(urljoin(BASE_PATH, visualizationUrl(editedNode.ref.id)));
            },
            className: "tertiary-button",
        };
    }, [editedNode, t]);

    //no process? no nodes? no window contents! no errors for whole tree!
    if (!scenario?.scenarioGraph.nodes) {
        return null;
    }

    const updatedEditedNode = scenarioGraph.nodes.find((node) => node.id === editedNode.id);
    const updatedReferenceEditedNode = { ...editedNode, ref: updatedEditedNode.ref };

    return (
        <WindowContent
            {...props}
            closeWithEsc
            buttons={[openFragment, cancel, apply]}
            title={getNodeDetailsModalTitle(node)}
            icon={<NodeDetailsModalIcon node={node} />}
            subheader={<NodeDetailsModalSubheader node={node} />}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
            }}
        >
            {validationResultPresent ? (
                <NodeGroupContent node={updatedReferenceEditedNode} edges={outputEdges} onChange={!readOnly && onChange} />
            ) : (
                <Box width={"100%"} height={"100px"} mt={1}>
                    <Box display={"flex"} justifyContent={"center"} height={"100%"} alignItems={"center"}>
                        <CircularProgress />
                    </Box>
                </Box>
            )}
        </WindowContent>
    );
}

export default NodeDetails;
