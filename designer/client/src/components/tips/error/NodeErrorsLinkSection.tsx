import { isEmpty } from "lodash";
import React, { SyntheticEvent } from "react";
import { useSelector } from "react-redux";
import NodeUtils from "../../graph/NodeUtils";
import { getProcessUnsavedNewName } from "../../../reducers/selectors/graph";
import { NodeId, NodeType, Process } from "../../../types";
import { ErrorHeader } from "./ErrorHeader";
import { NodeErrorLink } from "./NodeErrorLink";

interface NodeErrorsLinkSectionProps {
    nodeIds: NodeId[];
    message: string;
    showDetails: (event: SyntheticEvent, details: NodeType) => void;
    currentProcess: Process;
    errorsOnTop?: boolean;
}

export default function NodeErrorsLinkSection(props: NodeErrorsLinkSectionProps): JSX.Element {
    const { nodeIds, message, showDetails, currentProcess, errorsOnTop } = props;
    const unsavedName = useSelector(getProcessUnsavedNewName);
    const separator = ", ";

    return (
        !isEmpty(nodeIds) && (
            <div style={{ marginTop: errorsOnTop && 5 }}>
                <ErrorHeader message={message} />
                {nodeIds.map((nodeId, index) => {
                    const details =
                        nodeId === "properties"
                            ? NodeUtils.getProcessPropertiesNode(currentProcess, unsavedName)
                            : NodeUtils.getNodeById(nodeId, currentProcess);
                    return (
                        <React.Fragment key={nodeId}>
                            <NodeErrorLink onClick={(event) => showDetails(event, details)} nodeId={nodeId} disabled={!details} />
                            {index < nodeIds.length - 1 ? separator : null}
                        </React.Fragment>
                    );
                })}
            </div>
        )
    );
}
