import React, { SetStateAction, useMemo } from "react";
import { Edge, NodeType, NodeValidationError } from "../../../types";
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox";
import { useSelector } from "react-redux";
import { getCurrentErrors } from "./NodeDetailsContent/selectors";
import { RootState } from "../../../reducers";
import { NodeTable } from "./NodeDetailsContent/NodeTable";
import { partition } from "lodash";
import NodeErrors from "./NodeErrors";
import { TestResultsWrapper } from "./TestResultsWrapper";
import { NodeTypeDetailsContent } from "./NodeTypeDetailsContent";
import { DebugNodeInspector } from "./NodeDetailsContent/DebugNodeInspector";
import { useUserSettings } from "../../../common/userSettings";

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

    return (
        <NodeTable>
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
            {/*<NodeAdditionalInfoBox node={node} />*/}
            {userSettings["debug.nodesAsJson"] && <DebugNodeInspector node={node} />}
        </NodeTable>
    );
};
