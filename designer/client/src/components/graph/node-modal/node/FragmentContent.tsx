import React, { useCallback, useState } from "react";
import { useSelector } from "react-redux";
import HttpService from "../../../../http/HttpService";
import { getProcessCounts, getStickyNotes } from "../../../../reducers/selectors/graph";
import { FragmentNodeType } from "../../../../types";
import { ErrorBoundary, DialogErrorFallbackComponent } from "../../../common/error-boundary";
import NodeUtils from "../../NodeUtils";
import { FragmentGraphPreview } from "../../fragmentGraph";
import { correctFetchedDetails } from "../../../../reducers/graph/correctFetchedDetails";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { getFragmentNodesPrefix, useModalDetailsIfNeeded } from "../../../../containers/hooks/useModalDetailsIfNeeded";
import { useInitEffect } from "../../../../containers/hooks/useInitEffect";
import { Scenario } from "../../../Process/types";

export function FragmentContent({ nodeToDisplay }: { nodeToDisplay: FragmentNodeType }): JSX.Element {
    const processCounts = useSelector(getProcessCounts);
    const stickyNotes = useSelector(getStickyNotes);
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const [fragmentContent, setFragmentContent] = useState<Scenario>(null);
    const { openFragmentNodes } = useModalDetailsIfNeeded();

    const initFragmentData = useCallback(async () => {
        if (fragmentContent) return;
        if (!NodeUtils.nodeIsFragment(nodeToDisplay)) return;

        const id = nodeToDisplay?.ref.id;
        const { data } = await HttpService.fetchProcessDetails(id);
        const scenario = correctFetchedDetails(data, processDefinitionData);
        setFragmentContent(scenario);
        openFragmentNodes(scenario);
    }, [fragmentContent, nodeToDisplay, openFragmentNodes, processDefinitionData]);

    useInitEffect(initFragmentData);

    const fragmentCounts = (processCounts[nodeToDisplay.id] || {}).fragmentCounts || {};

    return (
        <ErrorBoundary FallbackComponent={DialogErrorFallbackComponent}>
            {fragmentContent && (
                <FragmentGraphPreview
                    processCounts={fragmentCounts}
                    scenario={fragmentContent}
                    stickyNotes={stickyNotes}
                    nodeIdPrefixForFragmentTests={getFragmentNodesPrefix(fragmentContent)}
                />
            )}
        </ErrorBoundary>
    );
}
