import { isEmpty } from "lodash";
import type { SyntheticEvent } from "react";
import React from "react";
import { NavLink } from "react-router-dom";

import { visualizationUrl } from "../../../common/VisualizationUrl";
import type { NodeId, NodeType } from "../../../types/node";
import NodeUtils from "../../graph/NodeUtils";
import type { Scenario } from "../../Process/types";
import { ErrorHeader } from "./ErrorHeader";
import { NodeErrorLink } from "./NodeErrorLink";
import { ErrorLinkStyle } from "./styled";

interface NodeErrorsLinkSectionProps {
    nodeIds: NodeId[];
    message: string;
    showDetails: (event: SyntheticEvent, details: NodeType) => void;
    scenario: Scenario;
}

export default function NodeErrorsLinkSection(props: NodeErrorsLinkSectionProps): React.JSX.Element {
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
