import { isEmpty } from "lodash";
import React, { SyntheticEvent } from "react";
import { useSelector } from "react-redux";
import NodeUtils from "../../graph/NodeUtils";
import { getProcessUnsavedNewName } from "../../../reducers/selectors/graph";
import { NodeId, NodeType } from "../../../types";
import { ErrorHeader } from "./ErrorHeader";
import { NodeErrorLink } from "./NodeErrorLink";
import { Scenario } from "../../Process/types";

interface NodeErrorsLinkSectionProps {
    nodeIds: NodeId[];
    message: string;
    showDetails: (event: SyntheticEvent, details: NodeType) => void;
    scenario: Scenario;
}

export default function NodeErrorsLinkSection(props: NodeErrorsLinkSectionProps): JSX.Element {
    const { nodeIds, message, showDetails, scenario } = props;
    const unsavedName = useSelector(getProcessUnsavedNewName);
    const separator = ", ";

    return (
        !isEmpty(nodeIds) && (
            <div>
                <ErrorHeader message={message} />
                {nodeIds.map((nodeId, index) => {
                    const details =
                        nodeId === "properties"
                            ? NodeUtils.getProcessPropertiesNode(scenario, unsavedName)
                            : NodeUtils.getNodeById(nodeId, scenario.scenarioGraph);

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
