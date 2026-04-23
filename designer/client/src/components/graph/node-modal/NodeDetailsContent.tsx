import type { SetStateAction } from "react";
import React, { useCallback, useMemo } from "react";

import { determineComponentId } from "../../../common/componentUtils";
import { useUserSettings } from "../../../common/useUserSettings";
import { getConfiguredAdditionalComponents } from "../../../reducers/selectors/configuredAdditionalComponents";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getRemoteTenantId, getRemoteWebHost } from "../../../reducers/selectors/isCloudInstance";
import { useAppSelector } from "../../../store/storeHelpers";
import type { Edge } from "../../../types/edge";
import type { NodeType } from "../../../types/node";
import { NodeAdditionalInfo } from "./NodeAdditionalInfo";
import { DebugNodeInspector } from "./NodeDetailsContent/DebugNodeInspector";
import { getNodesDetails } from "./NodeDetailsContent/getNodeDetails";
import { NodeTable } from "./NodeDetailsContent/NodeTable";
import { getNodeIsValidating } from "./NodeDetailsContent/selectors";
import NodeErrors from "./NodeErrors";
import { NodeSwitcher } from "./NodeSwitcher";
import { NodeTypeDetailsContent } from "./NodeTypeDetailsContent";
import { TestResultsWrapper } from "./TestResultsWrapper";
import { useGetNodeErrors } from "./useNodeTypeDetailsContentLogic";

export const NodeDetailsContent = ({
    node,
    edges,
    onChange,
    showValidation,
    showSwitch,
    showTestResults,
}: {
    node: NodeType;
    edges?: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
    showValidation?: boolean;
    showSwitch?: boolean;
    showTestResults?: boolean;
}): React.JSX.Element => {
    const [errors, diagramStructureErrors] = useGetNodeErrors(node);
    const isValidating = useAppSelector((state) => getNodeIsValidating(state, { node }));

    const [showAggregateSwitcher, showIntegrationsCreators, showInputsAndOutputs, nodesAsJson] = useUserSettings(
        "node.showAggregateSwitcher",
        "cloud.showIntegrationsCreators",
        "node.showInputsAndOutputs",
        "debug.nodesAsJson",
    );

    const configuredAdditionalComponents = useAppSelector(getConfiguredAdditionalComponents);
    const creatorType = useMemo(() => {
        return getCreatorType(node) || configuredAdditionalComponents.find((c) => c.componentId === determineComponentId(node))?.type;
    }, [configuredAdditionalComponents, node]);

    const nodeSwitcherVisible = creatorType && (creatorType === "aggregate" ? showAggregateSwitcher : showIntegrationsCreators);

    const tenantId = useAppSelector(getRemoteTenantId);
    const cloudHost = useAppSelector(getRemoteWebHost);
    const goToCreator = useCallback(
        (creatorType: string) => () => window.open(`${cloudHost}/instance/${tenantId}/integrations/createIntegration/${creatorType}`),
        [cloudHost, tenantId],
    );

    const onCreate = useMemo(() => (creatorType === "aggregate" ? null : goToCreator(creatorType)), [creatorType, goToCreator]);

    return (
        <NodeTable sx={showInputsAndOutputs ? { margin: "0 16px" } : undefined}>
            {nodeSwitcherVisible ? (
                <NodeSwitcher
                    node={node}
                    edges={edges}
                    onChange={onChange}
                    creatorType={creatorType}
                    componentsNamesToSelect={
                        creatorType === "aggregate"
                            ? ["custom-aggregate-tumbling", "custom-aggregate-session", "custom-aggregate-sliding"]
                            : configuredAdditionalComponents.filter((c) => c.type === creatorType)?.map((c) => c.componentId) || []
                    }
                    onCreate={onCreate}
                />
            ) : null}
            {showValidation ? <NodeErrors errors={diagramStructureErrors} message="Node has errors" /> : null}
            <TestResultsWrapper nodeId={node.id} showTestResults={showTestResults}>
                <NodeTypeDetailsContent
                    node={node}
                    edges={edges}
                    onChange={onChange}
                    errors={errors}
                    showValidation={showValidation}
                    showSwitch={showSwitch}
                    isValidating={isValidating}
                />
            </TestResultsWrapper>
            <NodeAdditionalInfo node={node} />
            {nodesAsJson && <DebugNodeInspector node={node} />}
        </NodeTable>
    );
};
