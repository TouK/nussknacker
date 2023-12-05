import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import HttpService from "../../../../http/HttpService";
import { getProcessCounts } from "../../../../reducers/selectors/graph";
import { FragmentNodeType, Process } from "../../../../types";
import ErrorBoundary from "../../../common/ErrorBoundary";
import NodeUtils from "../../NodeUtils";
import { fragmentGraph as BareGraph } from "../../fragmentGraph";
import { correctFetchedDetails } from "../../../../reducers/graph/correctFetchedDetails";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";

export function FragmentContent({ nodeToDisplay }: { nodeToDisplay: FragmentNodeType }): JSX.Element {
    const processCounts = useSelector(getProcessCounts);
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const [fragmentContent, setFragmentContent] = useState<Process>(null);

    useEffect(() => {
        if (NodeUtils.nodeIsFragment(nodeToDisplay)) {
            const id = nodeToDisplay?.ref.id;
            HttpService.fetchProcessDetails(id).then((response) => {
                const fetchedProcessDetails = correctFetchedDetails(response.data, processDefinitionData);
                setFragmentContent(fetchedProcessDetails.json);
            });
        }
    }, [nodeToDisplay, processDefinitionData]);

    const fragmentCounts = (processCounts[nodeToDisplay.id] || {}).fragmentCounts || {};

    return (
        <ErrorBoundary>
            {fragmentContent && (
                <BareGraph
                    processCounts={fragmentCounts}
                    processToDisplay={fragmentContent}
                    nodeIdPrefixForFragmentTests={`${nodeToDisplay.id}-`}
                />
            )}
        </ErrorBoundary>
    );
}
