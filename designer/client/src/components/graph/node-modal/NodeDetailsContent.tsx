import { partition } from "lodash";
import React, { SetStateAction, useMemo } from "react";
import { useSelector } from "react-redux";
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
    const creatorType = getCreatorType(node);

    return (
        <NodeTable>
            <NodeSwitcher
                node={node}
                edges={edges}
                onChange={onChange}
                componentsNamesToSelect={
                    creatorType === "aggregate"
                        ? ["custom-aggregate-tumbling", "custom-aggregate-session", "custom-aggregate-sliding"]
                        : configuredAdditionalComponents[creatorType]?.map((c) => c.componentId) || []
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
