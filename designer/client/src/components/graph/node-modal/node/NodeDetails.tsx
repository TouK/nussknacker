import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { DefaultComponents as Window } from "@touk/window-manager";
import type { DefaultContentProps } from "@touk/window-manager/cjs/components/window/DefaultContent";
import { uniq } from "lodash";
import React, { memo, useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useDebouncedValue } from "rooks";
import urljoin from "url-join";

import { ToolId } from "../../../../actions/nk/toolWindow";
import { useUserSettings } from "../../../../common/useUserSettings";
import { visualizationUrl } from "../../../../common/VisualizationUrl";
import { BASE_PATH } from "../../../../config";
import type { RootState } from "../../../../reducers";
import { getCreatorType } from "../../../../reducers/selectors/getCreator";
import { getScenarioGraph } from "../../../../reducers/selectors/graph";
import { getActiveTestCase } from "../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { Edge } from "../../../../types/edge";
import type { NodeType } from "../../../../types/node";
import type { ScenarioGraph } from "../../../../types/scenarioGraph";
import { WindowContent } from "../../../../windowManager/WindowContent";
import type { WindowKind } from "../../../../windowManager/WindowKind";
import { useOnToolWindow } from "../../../modals/useOnToolWindow";
import type { Scenario } from "../../../Process/types";
import NodeUtils from "../../NodeUtils";
import { InputOutputContent } from "../io/InputOutputContent";
import { InputOutputContextProvider } from "../io/InputOutputContext";
import { usePortal } from "../io/usePortal";
import { getNodeDetailsModalTitle, NodeDetailsModalIcon, NodeDetailsModalSubheader } from "../nodeDetails/NodeDetailsModalHeader";
import { hasValidationTestCasesErrors } from "../NodeDetailsContent/selectors";
import { TestResultsWrapper } from "../TestResultsWrapper";
import { useGetNodeErrors } from "../useNodeTypeDetailsContentLogic";
import { EditStateFeedback } from "./EditStateFeedback";
import { GeneralContent } from "./NodeContent/GeneralContent";
import type { TabDef } from "./NodeContent/TabsWrapper";
import { NodeDetailsTab, TabsWrapper } from "./NodeContent/TabsWrapper";
import { TestingContent } from "./NodeContent/TestingContent";
import { TestCaseSelect } from "./NodeContent/TestingContentElements/TestCaseSelect";
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
    editedNode: NodeType;
    outputEdges: Edge[];
    performNodeEdit: (editedNode: NodeType, outputEdges: Edge[]) => Promise<void>;
    close: () => void;
    readOnly?: boolean;
}) {
    const applyDisabled = useMemo(() => !editedNode["$name" in editedNode ? "$name" : "name"]?.length, [editedNode]);
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

function NodeDetails(props: NodeDetailsProps): React.JSX.Element {
    const { t } = useTranslation();
    const { close, data } = props;
    const readOnly = useAppSelector((s: RootState) => getReadOnly(s, props.readOnly));

    const { node, editedNode, onChange, outputEdges, performNodeEdit, editState } = useNodeState(data.meta);

    const _savedScenarioGraph = useAppSelector(getScenarioGraph);
    const _scenarioGraphWithEditedNode = useMemo<ScenarioGraph>(() => {
        const nodeInGraph = _savedScenarioGraph.nodes.find((n) => n.id === editedNode.id);
        return nodeInGraph === editedNode
            ? _savedScenarioGraph
            : { ..._savedScenarioGraph, nodes: _savedScenarioGraph.nodes.map((n) => (n.id === editedNode.id ? editedNode : n)) };
    }, [_savedScenarioGraph, editedNode]);
    const [scenarioGraphWithEditedNode] = useDebouncedValue(_scenarioGraphWithEditedNode, 500);

    const [generalErrors] = useGetNodeErrors(node);
    const testCase = useAppSelector(getActiveTestCase);
    const hasNodeTestCasesErrors = useAppSelector((state) =>
        hasValidationTestCasesErrors(state, { nodeId: node.id, testCaseName: testCase?.name }),
    );

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

    const [showInputsAndOutputs, testingTabVisible] = useUserSettings("node.showInputsAndOutputs", "node.showTestingTab");
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

    const components: DefaultContentProps["components"] = useMemo(() => {
        if (showInputsAndOutputs) {
            return { Content, Footer };
        }
        return {};
    }, [showInputsAndOutputs, Content, Footer]);

    useOnToolWindow(ToolId.node, node.id);

    const nodeId = useMemo(() => {
        return uniq([editedNode.id, node.id].filter(Boolean));
    }, [editedNode.id, node.id]);

    const generalContent = useMemo(
        () => <GeneralContent node={editedNode} edges={outputEdges} onChange={readOnly ? undefined : onChange} />,
        [editedNode, onChange, outputEdges, readOnly],
    );

    const testingContent = useMemo(
        () => (
            <TestResultsWrapper nodeId={editedNode.id}>
                <TestingContent
                    node={editedNode}
                    edges={outputEdges}
                    onChange={readOnly ? undefined : onChange}
                    scenarioGraph={scenarioGraphWithEditedNode}
                />
            </TestResultsWrapper>
        ),
        [editedNode, onChange, outputEdges, readOnly, scenarioGraphWithEditedNode],
    );

    const tabs = useMemo<TabDef[]>(
        () => [
            {
                id: NodeDetailsTab.general,
                label: t("nodeDetails.tabs.general.name", "General"),
                content: generalContent,
                showErrorIndicator: generalErrors.length > 0,
            },
            {
                id: NodeDetailsTab.testing,
                label: t("nodeDetails.tabs.testing.name", "Testing"),
                content: testingContent,
                disabled: !testingTabVisible,
                additionalTabContent: <TestCaseSelect />,
                showErrorIndicator: hasNodeTestCasesErrors,
            },
        ],
        [generalContent, generalErrors.length, hasNodeTestCasesErrors, t, testingContent, testingTabVisible],
    );

    return (
        <InputOutputContextProvider nodeId={nodeId}>
            <EditStateFeedback editState={editState} />
            <WindowContent {...props} closeWithEsc buttons={buttons} {...titleData} components={components}>
                <TabsWrapper tabs={tabs} hideIfOne hideDisabled />
            </WindowContent>
        </InputOutputContextProvider>
    );
}

export default memo(NodeDetails);
