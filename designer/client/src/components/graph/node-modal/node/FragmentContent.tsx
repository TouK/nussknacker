import React, { useCallback, useState } from "react";

import { useInitEffect } from "../../../../containers/hooks/useInitEffect";
import { getFragmentNodesPrefix, useModalDetailsIfNeeded } from "../../../../containers/hooks/useModalDetailsIfNeeded";
import HttpService from "../../../../http/HttpService";
import { correctFetchedDetails } from "../../../../reducers/graph/correctFetchedDetails";
import { getProcessDefinitionData } from "../../../../reducers/selectors/getProcessDefinitionData";
import { getProcessCounts } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/configureStore";
import type { FragmentNodeType } from "../../../../types";
import { DialogErrorFallbackComponent, ErrorBoundary } from "../../../common/error-boundary";
import type { Scenario } from "../../../Process/types";
import { FragmentGraphPreview } from "../../fragmentGraph";
import NodeUtils from "../../NodeUtils";

export function FragmentContent({ nodeToDisplay }: { nodeToDisplay: FragmentNodeType }): JSX.Element {
    const processCounts = useAppSelector(getProcessCounts);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);

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
                    nodeIdPrefixForFragmentTests={getFragmentNodesPrefix(fragmentContent)}
                />
            )}
        </ErrorBoundary>
    );
}
