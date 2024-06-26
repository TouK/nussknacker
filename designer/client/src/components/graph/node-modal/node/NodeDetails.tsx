import { css } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useKey } from "rooks";
import urljoin from "url-join";
import { editNode } from "../../../../actions/nk";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { BASE_PATH } from "../../../../config";
import { isInputTarget } from "../../../../containers/BindKeyboardShortcuts";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../../../containers/hooks/useSearchQuery";
import { RootState } from "../../../../reducers";
import { getScenario } from "../../../../reducers/selectors/graph";
import { Edge, NodeType } from "../../../../types";
import { WindowContent, WindowKind } from "../../../../windowManager";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import ErrorBoundary from "../../../common/ErrorBoundary";
import { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import { applyIdFromFakeName } from "../IdField";
import { getNodeDetailsModalTitle, NodeDetailsModalIcon, NodeDetailsModalSubheader } from "../nodeDetails/NodeDetailsModalHeader";
import { NodeGroupContent } from "./NodeGroupContent";
import { getReadOnly } from "./selectors";

function mergeQuery(changes: Record<string, string[]>) {
    return replaceSearchQuery((current) => ({ ...current, ...changes }));
}

interface NodeDetailsProps extends WindowContentProps<WindowKind, { node: NodeType; scenario: Scenario }> {
    readOnly?: boolean;
}

export function NodeDetails(props: NodeDetailsProps): JSX.Element {
    const scenarioFromGlobalStore = useSelector(getScenario);
    const readOnly = useSelector((s: RootState) => getReadOnly(s, props.readOnly));

    const { node, scenario = scenarioFromGlobalStore } = props.data.meta;
    const [editedNode, setEditedNode] = useState<NodeType>(node);
    const [outputEdges, setOutputEdges] = useState(() => scenario.scenarioGraph.edges.filter(({ from }) => from === node.id));

    const onChange = useCallback((node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
        setEditedNode(node);
        setOutputEdges(edges);
    }, []);

    const dispatch = useDispatch();

    const performNodeEdit = useCallback(async () => {
        try {
            //TODO: without removing nodeId query param, the dialog after close, is opening again. It looks like useModalDetailsIfNeeded is fired after edit, because nodeId is still in the query string params, after scenario changes.
            mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));
            await dispatch(editNode(scenario, node, applyIdFromFakeName(editedNode), outputEdges));
            props.close();
        } catch (e) {
            //TODO: It's a workaround and continuation of above TODO, let's revert query param deletion, if dialog is still open because of server error
            mergeQuery(parseWindowsQueryParams({ nodeId: node.id }, {}));
        }
    }, [scenario, node, editedNode, outputEdges, dispatch, props]);

    const { t } = useTranslation();

    const applyButtonData: WindowButtonProps | null = useMemo(
        () =>
            !readOnly
                ? {
                      title: t("dialog.button.apply", "apply"),
                      action: performNodeEdit,
                      disabled: !editedNode.id?.length,
                  }
                : null,
        [editedNode.id?.length, performNodeEdit, readOnly, t],
    );

    const openFragmentButtonData: WindowButtonProps | null = useMemo(
        () =>
            NodeUtils.nodeIsFragment(editedNode)
                ? {
                      title: t("dialog.button.fragment.edit", "edit fragment"),
                      action: () => {
                          window.open(urljoin(BASE_PATH, visualizationUrl(editedNode.ref.id)));
                      },
                      classname: "tertiary-button",
                  }
                : null,
        [editedNode, t],
    );

    const cancelButtonData = useMemo(
        () => ({ title: t("dialog.button.cancel", "cancel"), action: props.close, classname: LoadingButtonTypes.secondaryButton }),
        [props, t],
    );

    useKey("Escape", (e) => {
        e.preventDefault();
        if (!isInputTarget(e.composedPath().shift())) {
            props.close();
        }
    });

    const buttons: WindowButtonProps[] = useMemo(
        () => [openFragmentButtonData, cancelButtonData, applyButtonData].filter(Boolean),
        [applyButtonData, cancelButtonData, openFragmentButtonData],
    );

    useEffect(() => {
        mergeQuery(parseWindowsQueryParams({ nodeId: node.id }));
        return () => {
            mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));
        };
    }, [node.id]);

    //no process? no nodes? no window contents! no errors for whole tree!
    if (!scenarioFromGlobalStore?.scenarioGraph.nodes) {
        return null;
    }

    return (
        <WindowContent
            {...props}
            title={getNodeDetailsModalTitle(node)}
            buttons={buttons}
            icon={<NodeDetailsModalIcon node={node} />}
            subheader={<NodeDetailsModalSubheader node={node} />}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
            }}
        >
            <ErrorBoundary>
                <NodeGroupContent node={editedNode} edges={outputEdges} onChange={!readOnly && onChange} />
            </ErrorBoundary>
        </WindowContent>
    );
}

export default NodeDetails;
