import React, { useCallback, useState } from "react";
import { useSelector } from "react-redux";
import HttpService from "../../../../http/HttpService";
import { getProcessCounts } from "../../../../reducers/selectors/graph";
import { FragmentNodeType, Process } from "../../../../types";
import ErrorBoundary from "../../../common/ErrorBoundary";
import NodeUtils from "../../NodeUtils";
import { FragmentGraphPreview } from "../../fragmentGraph";
import { correctFetchedDetails } from "../../../../reducers/graph/correctFetchedDetails";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { getFragmentNodesPrefix, useModalDetailsIfNeeded } from "../../../../containers/hooks/useModalDetailsIfNeeded";
import { usePromiseEffect } from "../../../../containers/hooks/usePromiseEffect";

export function FragmentContent({ nodeToDisplay }: { nodeToDisplay: FragmentNodeType }): JSX.Element {
    const processCounts = useSelector(getProcessCounts);
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const [fragmentContent, setFragmentContent] = useState<Process>(null);
    const { openFragmentNodes } = useModalDetailsIfNeeded();

    usePromiseEffect(
        useCallback(async () => {
            if (fragmentContent) return;
            if (!NodeUtils.nodeIsFragment(nodeToDisplay)) return;

            const id = nodeToDisplay?.ref.id;
            const { data } = await HttpService.fetchProcessDetails(id);
            const fetchedProcessDetails = correctFetchedDetails(data, processDefinitionData);
            setFragmentContent(fetchedProcessDetails.json);
            openFragmentNodes(fetchedProcessDetails.json);
        }, [fragmentContent, nodeToDisplay, openFragmentNodes, processDefinitionData]),
    );

    const fragmentCounts = (processCounts[nodeToDisplay.id] || {}).fragmentCounts || {};

    return (
        <ErrorBoundary>
            {fragmentContent && (
                <FragmentGraphPreview
                    processCounts={fragmentCounts}
                    processToDisplay={fragmentContent}
                    nodeIdPrefixForFragmentTests={getFragmentNodesPrefix(fragmentContent)}
                />
            )}
        </ErrorBoundary>
    );
}
