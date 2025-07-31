import { partition } from "lodash";
import type { SetStateAction } from "react";
import React, { useCallback, useMemo } from "react";

import ProcessUtils from "../../../common/ProcessUtils";
import { useUserSettings } from "../../../common/userSettings";
import HttpService from "../../../http/HttpService";
import type { RootState } from "../../../reducers";
import { getConfiguredAdditionalComponents } from "../../../reducers/selectors/configuredAdditionalComponents";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getRemoteTenantId, getRemoteWebHost } from "../../../reducers/selectors/isCloudInstance";
import { useAppSelector } from "../../../store/configureStore";
import type { Edge, NodeType, NodeValidationError } from "../../../types";
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox";
import { DebugNodeInspector } from "./NodeDetailsContent/DebugNodeInspector";
import { NodeTable } from "./NodeDetailsContent/NodeTable";
import { getCurrentErrors } from "./NodeDetailsContent/selectors";
import NodeErrors from "./NodeErrors";
import { NodeSwitcher } from "./NodeSwitcher";
import { NodeTypeDetailsContent } from "./NodeTypeDetailsContent";
import { TestResultsWrapper } from "./TestResultsWrapper";

export const NodeDetailsContent = ({
    node,
    edges,
    onChange,
    nodeErrors,
    showValidation,
    showSwitch,
    showTestResults,
}: {
    node: NodeType;
    edges?: Edge[];
    onChange?: (node: SetStateAction<NodeType>, edges?: SetStateAction<Edge[]>) => void;
    nodeErrors?: NodeValidationError[];
    showValidation?: boolean;
    showSwitch?: boolean;
    showTestResults?: boolean;
}): JSX.Element => {
    const currentErrors = useAppSelector((state: RootState) => getCurrentErrors(state)(node.id, nodeErrors));
    const [errors, diagramStructureErrors] = useMemo(() => partition(currentErrors, (error) => !!error.fieldName), [currentErrors]);

    const [userSettings] = useUserSettings();

    const configuredAdditionalComponents = useAppSelector(getConfiguredAdditionalComponents);
    const creatorType = useMemo(() => {
        return (
            getCreatorType(node) ||
            configuredAdditionalComponents.find((c) => c.componentId === ProcessUtils.determineComponentId(node))?.type
        );
    }, [configuredAdditionalComponents, node]);

    const nodeSwitcherVisible =
        creatorType &&
        (creatorType === "aggregate" ? userSettings["node.showAggregateSwitcher"] : userSettings["cloud.showIntegrationsCreators"]);

    const tenantId = useAppSelector(getRemoteTenantId);
    const cloudHost = useAppSelector(getRemoteWebHost);
    const goToCreator = useCallback(
        (creatorType: string) => () => window.open(`${cloudHost}/instance/${tenantId}/integrations/createIntegration/${creatorType}`),
        [cloudHost, tenantId],
    );

    const onCreate = useMemo(() => (creatorType === "aggregate" ? null : goToCreator(creatorType)), [creatorType, goToCreator]);

    return (
        <NodeTable sx={userSettings["node.showInputsAndOutputs"] ? { margin: "0 16px" } : undefined}>
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
                />
            </TestResultsWrapper>
            <NodeAdditionalInfoBox node={node} handleGetAdditionalInfo={HttpService.getNodeAdditionalInfo} />
            {userSettings["debug.nodesAsJson"] && <DebugNodeInspector node={node} />}
        </NodeTable>
    );
};
