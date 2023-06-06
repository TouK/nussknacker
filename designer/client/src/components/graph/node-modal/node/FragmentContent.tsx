import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import HttpService from "../../../../http/HttpService";
import { getProcessCounts } from "../../../../reducers/selectors/graph";
import { Process, FragmentNodeType } from "../../../../types";
import ErrorBoundary from "../../../common/ErrorBoundary";
import NodeUtils from "../../NodeUtils";
import { fragmentGraph as BareGraph } from "../../fragmentGraph";

export function FragmentContent({ nodeToDisplay }: { nodeToDisplay: FragmentNodeType }): JSX.Element {
    const processCounts = useSelector(getProcessCounts);

    const [fragmentContent, setFragmentContent] = useState<Process>(null);

    useEffect(() => {
        if (NodeUtils.nodeIsFragment(nodeToDisplay)) {
            const id = nodeToDisplay?.ref.id;
            HttpService.fetchProcessDetails(id).then((response) => {
                setFragmentContent(response.data.json);
            });
        }
    }, [nodeToDisplay]);

    const fragmentCounts = (processCounts[nodeToDisplay.id] || {}).fragmentCounts || {};

    return (
        <ErrorBoundary>
            {fragmentContent && (
                <BareGraph
                    processCounts={fragmentCounts}
                    processToDisplay={fragmentContent}
                    nodeIdPrefixForFragmentTests={`${fragmentContent.id}-`}
                />
            )}
        </ErrorBoundary>
    );
}
