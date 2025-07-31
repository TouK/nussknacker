import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { DefaultComponents as Window } from "@touk/window-manager";
import type { DefaultContentProps } from "@touk/window-manager/cjs/components/window/DefaultContent";
import type { HeaderButtonCloseProps } from "@touk/window-manager/cjs/components/window/header/HeaderButtonClose";
import React, { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import urljoin from "url-join";

import { nodeDetailsClosed, nodeDetailsOpened } from "../../../../actions/nk";
import { useUserSettings } from "../../../../common/userSettings";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { BASE_PATH } from "../../../../config";
import type { RootState } from "../../../../reducers";
import { removeHistorySnapshot, takeHistorySnapshot } from "../../../../reducers/graph/historySquash";
import { getCreatorType } from "../../../../reducers/selectors/getCreator";
import { useAppDispatch, useAppSelector } from "../../../../store/configureStore";
import type { Edge, NodeType } from "../../../../types";
import type { WindowKind } from "../../../../windowManager";
import { WindowContent } from "../../../../windowManager";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import type { EditedNode } from "../IdField";
import { InputOutputContent } from "../io/InputOutputContent";
import { InputOutputContextProvider } from "../io/InputOutputContext";
import { usePortal } from "../io/usePortal";
import { getNodeDetailsModalTitle, NodeDetailsModalIcon, NodeDetailsModalSubheader } from "../nodeDetails/NodeDetailsModalHeader";
import { EditStateFeedback } from "./EditStateFeedback";
import { NodeGroupContent } from "./NodeGroupContent";
import { getReadOnly } from "./selectors";
import type { EditState } from "./useNodeState";
import { useNodeState } from "./useNodeState";

export type NodeDetailsMeta = {
    node: NodeType;
    scenario: Scenario;
};

export type NodeDetailsProps = WindowContentProps<WindowKind, NodeDetailsMeta> & {
    readOnly?: boolean;
};

export function useNodeDetailsButtons({
    editedNode,
    outputEdges,
    performNodeEdit,
    close,
    readOnly,
}: {
    editedNode: EditedNode;
    outputEdges: Edge[];
    performNodeEdit: (editedNode: EditedNode, outputEdges: Edge[]) => Promise<void>;
    close: () => void;
    readOnly?: boolean;
}) {
    const { t } = useTranslation();
    const [settings] = useUserSettings();

    const autoApply = settings["node.autoApply"];
    const showInputsAndOutputs = settings["node.showInputsAndOutputs"];

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (readOnly) return false;
        if (autoApply) return false;
        return {
            title: t("dialog.button.apply", "apply"),
            action: () =>
                performNodeEdit(editedNode, outputEdges).then(() => {
                    close();
                }),
            disabled: !editedNode["$id" in editedNode ? "$id" : "id"]?.length,
        };
    }, [autoApply, close, editedNode, outputEdges, performNodeEdit, readOnly, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        if (autoApply && showInputsAndOutputs) return false;
        return {
            title: autoApply ? t("dialog.button.close", "close") : t("dialog.button.cancel", "cancel"),
            action: () => close(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [autoApply, close, showInputsAndOutputs, t]);

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

const CloseButton = ({ closeDialog, editStateRef }: HeaderButtonCloseProps & { editStateRef: React.RefObject<EditState> }) => {
    return (
        <Window.HeaderButtonClose
            closeDialog={() => {
                function close(i = 0) {
                    if (editStateRef?.current === "idle" || i >= 10) return closeDialog();
                    setTimeout(() => close(++i), 200);
                }
                close();
            }}
        />
    );
};

function NodeDetails(props: NodeDetailsProps): JSX.Element {
    const { t } = useTranslation();
    const { close, data } = props;
    const readOnly = useAppSelector((s: RootState) => getReadOnly(s, props.readOnly));

    const { node, editedNode, onChange, scenario, outputEdges, performNodeEdit, editState, editStateRef } = useNodeState(data.meta);
    const { cancel, apply } = useNodeDetailsButtons({ editedNode, outputEdges, performNodeEdit, close, readOnly });

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

    const components: DefaultContentProps["components"] = useMemo(() => {
        return settings["node.showInputsAndOutputs"]
            ? {
                  Content: (props) => <InputOutputContent {...props} ref={portalRef} />,
                  Footer: (props) => (
                      <PortalWrapper>
                          <Window.Footer {...props} />
                      </PortalWrapper>
                  ),
                  HeaderButtonClose: (props) => <CloseButton {...props} editStateRef={editStateRef} />,
              }
            : { HeaderButtonClose: (props) => <CloseButton {...props} editStateRef={editStateRef} /> };
    }, [settings, portalRef, PortalWrapper, editStateRef]);

    const dispatch = useAppDispatch();
    useEffect(() => {
        dispatch(nodeDetailsOpened(node.id, data.id));
        return () => {
            dispatch(nodeDetailsClosed(node.id, data.id));
        };
    }, [data.id, dispatch, node.id]);

    useEffect(() => {
        dispatch(takeHistorySnapshot());
        return () => {
            dispatch(removeHistorySnapshot());
        };
    }, [dispatch]);

    //no process? no nodes? no window contents! no errors for whole tree!
    if (!scenario?.scenarioGraph.nodes) {
        return null;
    }

    return (
        <InputOutputContextProvider nodeId={editedNode.id}>
            {settings["node.autoApply"] ? <EditStateFeedback editState={editState} /> : null}
            <WindowContent {...props} closeWithEsc={editState === "idle"} buttons={buttons} {...titleData} components={components}>
                <NodeGroupContent node={editedNode} edges={outputEdges} onChange={!readOnly && onChange} />
            </WindowContent>
        </InputOutputContextProvider>
    );
}

export default NodeDetails;
