import { css } from "@emotion/css";
import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { editNode } from "../../../../actions/nk";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { Edge, NodeType } from "../../../../types";
import { WindowContent, WindowKind } from "../../../../windowManager";
import ErrorBoundary from "../../../common/ErrorBoundary";
import NodeUtils from "../../NodeUtils";
import NodeDetailsModalHeader from "../nodeDetails/NodeDetailsModalHeader";
import { NodeGroupContent } from "./NodeGroupContent";
import { getReadOnly } from "./selectors";
import urljoin from "url-join";
import { BASE_PATH } from "../../../../config";
import { RootState } from "../../../../reducers";
import { applyIdFromFakeName } from "../IdField";
import { useTheme } from "@mui/material";
import { alpha, tint } from "../../../../containers/theme/helpers";
import { parseWindowsQueryParams, replaceSearchQuery } from "../../../../containers/hooks/useSearchQuery";
import { Scenario } from "../../../Process/types";
import { getScenario } from "../../../../reducers/selectors/graph";

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
    const [outputEdges, setOutputEdges] = useState(() => scenario.json.edges.filter(({ from }) => from === node.id));

    const onChange = useCallback((node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => {
        setEditedNode(node);
        setOutputEdges(edges);
    }, []);

    const dispatch = useDispatch();

    const performNodeEdit = useCallback(async () => {
        await dispatch(await editNode(scenario, node, applyIdFromFakeName(editedNode), outputEdges));

        //TODO: without removing nodeId query param, the dialog after close, is opening again. It looks like props.close doesn't unmount component.
        mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));
        props.close();
    }, [scenario, node, editedNode, outputEdges, dispatch, props]);

    const { t } = useTranslation();
    const theme = useTheme();

    const applyButtonData: WindowButtonProps | null = useMemo(
        () =>
            !readOnly
                ? {
                      title: t("dialog.button.apply", "apply"),
                      action: () => performNodeEdit(),
                      disabled: !editedNode.id?.length,
                      classname: css({
                          //increase (x4) specificity over ladda
                          "&&&&": {
                              backgroundColor: theme.custom.colors.accent,
                              ":hover": {
                                  backgroundColor: tint(theme.custom.colors.accent, 0.25),
                              },
                              "&[disabled], &[data-loading]": {
                                  "&, &:hover": {
                                      backgroundColor: alpha(theme.custom.colors.accent, 0.5),
                                  },
                              },
                          },
                      }),
                  }
                : null,
        [editedNode.id?.length, performNodeEdit, readOnly, t, theme.custom.colors.accent],
    );

    const openFragmentButtonData: WindowButtonProps | null = useMemo(
        () =>
            NodeUtils.nodeIsFragment(editedNode)
                ? {
                      title: t("dialog.button.fragment.edit", "edit fragment"),
                      action: () => {
                          window.open(urljoin(BASE_PATH, visualizationUrl(editedNode.ref.id)));
                      },
                  }
                : null,
        [editedNode, t],
    );

    const cancelButtonData = useMemo(
        () => ({ title: t("dialog.button.cancel", "cancel"), action: () => props.close(), classname: "window-close" }),
        [props, t],
    );

    const buttons: WindowButtonProps[] = useMemo(
        () => [openFragmentButtonData, cancelButtonData, applyButtonData].filter(Boolean),
        [applyButtonData, cancelButtonData, openFragmentButtonData],
    );

    const components = useMemo(() => {
        const HeaderTitle = () => <NodeDetailsModalHeader node={node} />;
        return { HeaderTitle };
    }, [node]);

    useEffect(() => {
        mergeQuery(parseWindowsQueryParams({ nodeId: node.id }));
        return () => {
            mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));
        };
    }, [node.id]);

    //no process? no nodes? no window contents! no errors for whole tree!
    if (!scenarioFromGlobalStore?.json.nodes) {
        return null;
    }

    return (
        <WindowContent
            {...props}
            buttons={buttons}
            components={components}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 } }),
            }}
        >
            <ErrorBoundary>
                <NodeGroupContent node={editedNode} edges={outputEdges} onChange={!readOnly && onChange} />
            </ErrorBoundary>
        </WindowContent>
    );
}

export default NodeDetails;
