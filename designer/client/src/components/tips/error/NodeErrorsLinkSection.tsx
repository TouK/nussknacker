import { isEmpty } from "lodash";
import React, { SyntheticEvent } from "react";
import NodeUtils from "../../graph/NodeUtils";
import { NodeId, NodeType } from "../../../types";
import { ErrorHeader } from "./ErrorHeader";
import { NodeErrorLink } from "./NodeErrorLink";
import { Scenario } from "../../Process/types";
import { NavLink } from "react-router-dom";
import { ErrorLinkStyle } from "./styled";
import { visualizationUrl } from "../../../common/VisualizationUrl";

interface NodeErrorsLinkSectionProps {
    nodeIds: NodeId[];
    message: string;
    showDetails: (event: SyntheticEvent, details: NodeType) => void;
    scenario: Scenario;
}

export default function NodeErrorsLinkSection(props: NodeErrorsLinkSectionProps): JSX.Element {
    const { nodeIds, message, showDetails, scenario } = props;
    const separator = ", ";

    return (
        !isEmpty(nodeIds) && (
            <div>
                <ErrorHeader message={message} />
                {nodeIds.map((nodeId, index) => {
                    const isFragmentNodeReference = NodeUtils.isFragmentNodeReference(nodeId, scenario.scenarioGraph);
                    if (isFragmentNodeReference) {
                        const { fragmentId, fragmentNodeId } = NodeUtils.getDetailsFromFragmentNode(nodeId, scenario.scenarioGraph);
                        return (
                            <React.Fragment key={nodeId}>
                                <ErrorLinkStyle
                                    variant={"body2"}
                                    component={NavLink}
                                    target={"_blank"}
                                    to={visualizationUrl(fragmentId, fragmentNodeId)}
                                >
                                    {nodeId}
                                </ErrorLinkStyle>
                                {index < nodeIds.length - 1 ? separator : null}
                            </React.Fragment>
                        );
                    }
                    const details = NodeUtils.getNodeById(nodeId, scenario.scenarioGraph);
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
