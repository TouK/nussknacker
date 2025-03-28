import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { DefaultComponents as Window } from "@touk/window-manager";
import React, { createContext, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import urljoin from "url-join";

import { useUserSettings } from "../../../../common/userSettings";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { BASE_PATH } from "../../../../config";
import { parseWindowsQueryParams } from "../../../../containers/hooks/useSearchQuery";
import type { RootState } from "../../../../reducers";
import { getCreatorType } from "../../../../reducers/selectors/getCreator";
import type { NodeType } from "../../../../types";
import type { WindowKind } from "../../../../windowManager";
import { WindowContent } from "../../../../windowManager";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import { InputOutputContent } from "../io/InputOutputContent";
import { InputOutputContextProvider } from "../io/InputOutputContext";
import { usePortal } from "../io/usePortal";
import { getNodeDetailsModalTitle, NodeDetailsModalIcon, NodeDetailsModalSubheader } from "../nodeDetails/NodeDetailsModalHeader";
import { NodeGroupContent } from "./NodeGroupContent";
import { getReadOnly } from "./selectors";
import { mergeQuery, useNodeState } from "./useNodeState";

export type NodeDetailsMeta = {
    node: NodeType;
    scenario: Scenario;
};

export type NodeDetailsProps = WindowContentProps<WindowKind, NodeDetailsMeta> & {
    readOnly?: boolean;
};

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

function useTitleData(node: NodeType) {
    const creatorType = node.type === "VariableBuilder" ? getCreatorType(node) : null;
    if (creatorType) {
        return {
            title: `${creatorType} creator`,
            icon: <NodeDetailsModalIcon node={node} />,
        };
    }
    return {
        title: getNodeDetailsModalTitle(node),
        icon: <NodeDetailsModalIcon node={node} />,
        subheader: <NodeDetailsModalSubheader node={node} />,
    };
}

export const NodeContext = createContext<NodeType>(null);

function NodeDetails(props: NodeDetailsProps): JSX.Element {
    const { t } = useTranslation();
    const { close, data } = props;
    const readOnly = useSelector((s: RootState) => getReadOnly(s, props.readOnly));

    const { node, editedNode, onChange, scenario, outputEdges, performNodeEdit } = useNodeState(data.meta);
    const { cancel, apply } = useNodeDetailsButtons({ editedNode, performNodeEdit, close, readOnly });

    useEffect(() => {
        mergeQuery(parseWindowsQueryParams({ nodeId: node.id }));
        return () => {
            mergeQuery(parseWindowsQueryParams({}, { nodeId: node.id }));
        };
    }, [node.id]);

    const nodeIsFragment = useMemo(() => NodeUtils.nodeIsFragment(editedNode), [editedNode]);

    const openFragment = useMemo<WindowButtonProps | false>(() => {
        if (!nodeIsFragment) return false;
        return {
            title: t("dialog.button.fragment.edit", "edit fragment"),
            action: () => {
                window.open(urljoin(BASE_PATH, visualizationUrl(editedNode?.ref?.id)));
            },
            className: "tertiary-button",
        };
    }, [editedNode?.ref?.id, nodeIsFragment, t]);

    const titleData = useTitleData(node);
    const buttons = useMemo(() => [openFragment, cancel, apply].filter(Boolean) as WindowButtonProps[], [apply, cancel, openFragment]);

    const [settings] = useUserSettings();
    const [PortalWrapper, portalRef] = usePortal();
    const components = useMemo(
        () =>
            settings["node.showInputsAndOutputs"]
                ? {
                      Content: (props) => <InputOutputContent {...props} ref={portalRef} />,
                      Footer: (props) => (
                          <PortalWrapper>
                              <Window.Footer {...props} />
                          </PortalWrapper>
                      ),
                  }
                : undefined,
        [settings, portalRef, PortalWrapper],
    );

    //no process? no nodes? no window contents! no errors for whole tree!
    if (!scenario?.scenarioGraph.nodes) {
        return null;
    }

    return (
        <NodeContext.Provider value={editedNode}>
            <InputOutputContextProvider nodeId={editedNode.id}>
                <WindowContent {...props} closeWithEsc buttons={buttons} {...titleData} components={components}>
                    <NodeGroupContent node={editedNode} edges={outputEdges} onChange={!readOnly && onChange} />
                </WindowContent>
            </InputOutputContextProvider>
        </NodeContext.Provider>
    );
}

export default NodeDetails;
