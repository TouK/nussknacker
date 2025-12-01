import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { DefaultComponents as Window } from "@touk/window-manager";
import type { DefaultContentProps } from "@touk/window-manager/cjs/components/window/DefaultContent";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import urljoin from "url-join";

import { ToolId } from "../../../../actions/nk/toolWindow";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { BASE_PATH } from "../../../../config";
import type { RootState } from "../../../../reducers";
import { getCreatorType } from "../../../../reducers/selectors/getCreator";
import { getTestResultsLoading } from "../../../../reducers/selectors/graph";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import { WindowContent } from "../../../../windowManager/WindowContent";
import type { WindowKind } from "../../../../windowManager/WindowKind";
import { useTestingScenarioEnabled } from "../../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { useOnToolWindow } from "../../../modals/useOnToolWindow";
import type { Scenario } from "../../../Process/types";
import { CustomButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import { useGetButtonFromToolbar } from "../../../toolbarSettings/useToolbarConfig";
import NodeUtils from "../../NodeUtils";
import type { EditedNode } from "../IdField";
import { InputOutputContent } from "../io/InputOutputContent";
import { InputOutputContextProvider } from "../io/InputOutputContext";
import { usePortal } from "../io/usePortal";
import { getNodeDetailsModalTitle, NodeDetailsModalIcon, NodeDetailsModalSubheader } from "../nodeDetails/NodeDetailsModalHeader";
import { CloseButtonWithEditLock } from "./CloseButtonWithEditLock";
import { EditStateFeedback } from "./EditStateFeedback";
import { GeneralContent } from "./NodeContent/GeneralContent";
import { TabsWrapper } from "./NodeContent/TabsWrapper";
import { TestingContent } from "./NodeContent/TestingContent";
import { getReadOnly } from "./selectors";
import { useDialogActions } from "./useDialogActions";
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
    const applyDisabled = useMemo(() => !editedNode["$id" in editedNode ? "$id" : "id"]?.length, [editedNode]);
    const onApply = useCallback(() => performNodeEdit(editedNode, outputEdges), [performNodeEdit, editedNode, outputEdges]);
    return useDialogActions({ readOnly, onApply, onClose: close, paused: applyDisabled });
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

function NodeDetails(props: NodeDetailsProps): JSX.Element {
    const { t } = useTranslation();
    const { close, data } = props;
    const readOnly = useAppSelector((s: RootState) => getReadOnly(s, props.readOnly));
    const buttonFromToolbar = useGetButtonFromToolbar(CustomButtonTypes.scenarioTest);

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
    const testResultsLoading = useAppSelector(getTestResultsLoading);

    const settings = useAppSelector(getUserSettings);
    const [PortalWrapper, portalRef] = usePortal();

    const Content: DefaultContentProps["components"]["Content"] = useCallback(
        (props) => {
            return <InputOutputContent {...props} ref={portalRef} />;
        },
        [portalRef],
    );

    const Footer: DefaultContentProps["components"]["Footer"] = useCallback(
        (props) => {
            return (
                <PortalWrapper>
                    <Window.Footer {...props} />
                </PortalWrapper>
            );
        },
        [PortalWrapper],
    );

    const HeaderButtonClose: DefaultContentProps["components"]["HeaderButtonClose"] = useCallback(
        (props) => {
            return <CloseButtonWithEditLock {...props} editStateRef={editStateRef} />;
        },
        [editStateRef],
    );

    const components: DefaultContentProps["components"] = useMemo(() => {
        if (settings["node.showInputsAndOutputs"]) {
            return { Content, Footer, HeaderButtonClose };
        }
        return { HeaderButtonClose };
    }, [settings, Content, Footer, HeaderButtonClose]);

    useOnToolWindow(ToolId.node, node.id);

    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled: buttonFromToolbar.disabled });

    //no process? no nodes? no window contents! no errors for whole tree!
    if (!scenario?.scenarioGraph.nodes) {
        return null;
    }

    return (
        <InputOutputContextProvider nodeId={editedNode.id}>
            {settings["node.autoApply"] ? <EditStateFeedback editState={editState} /> : null}

            <WindowContent {...props} closeWithEsc={editState === "idle"} buttons={buttons} {...titleData} components={components}>
                {testingScenarioEnabled ? (
                    <TabsWrapper
                        tabs={[
                            {
                                label: "General",
                                content: (
                                    <GeneralContent node={editedNode} edges={outputEdges} onChange={readOnly ? undefined : onChange} />
                                ),
                            },
                            {
                                label: "Testing",
                                content: <TestingContent node={editedNode} />,
                                isLoading: testResultsLoading,
                            },
                        ]}
                    />
                ) : (
                    <GeneralContent node={editedNode} edges={outputEdges} onChange={readOnly ? undefined : onChange} />
                )}
            </WindowContent>
        </InputOutputContextProvider>
    );
}

export default NodeDetails;
