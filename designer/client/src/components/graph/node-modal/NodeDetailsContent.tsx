import { partition } from "lodash";
import React, { SetStateAction, useMemo } from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { useUserSettings } from "../../../common/userSettings";
import HttpService from "../../../http/HttpService";
import { RootState } from "../../../reducers";
import { getConfiguredAdditionalComponents } from "../../../reducers/selectors/getComponentGroups";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { Edge, NodeType, NodeValidationError } from "../../../types";
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
    const currentErrors = useSelector((state: RootState) => getCurrentErrors(state)(node.id, nodeErrors));
    const [errors, diagramStructureErrors] = useMemo(() => partition(currentErrors, (error) => !!error.fieldName), [currentErrors]);

    const [userSettings] = useUserSettings();

    const configuredAdditionalComponents = useSelector(getConfiguredAdditionalComponents);
    const creatorType = useMemo(() => {
        return (
            getCreatorType(node) ||
            configuredAdditionalComponents.find((c) => c.componentId === ProcessUtils.determineComponentId(node))?.type
        );
    }, [configuredAdditionalComponents, node]);

    return (
        <NodeTable sx={userSettings["node.showInputsAndOutputs"] ? { margin: "0 16px" } : undefined}>
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
                onCreate={
                    creatorType === "aggregate"
                        ? null
                        : () => {
                              const tenantId = `55cf1666-e91e-42cb-80cd-f34f8b08e2b1`;
                              window.open(`https://manage.staging-cloud.nussknacker.io/instance/${tenantId}/createEnricher/${creatorType}`);
                          }
                }
            />
            <NodeErrors errors={diagramStructureErrors} message="Node has errors" />
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
