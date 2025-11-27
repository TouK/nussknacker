import { Box } from "@mui/material";
import React, { useCallback, useState } from "react";

import { useInitEffect } from "../../../../containers/hooks/useInitEffect";
import { getFragmentNodesPrefix, useModalsIfNeeded } from "../../../../containers/hooks/useModalsIfNeeded";
import HttpService from "../../../../http/HttpService/instance";
import { correctFetchedDetails } from "../../../../reducers/graph/correctFetchedDetails";
import { getProcessDefinitionData } from "../../../../reducers/selectors/getProcessDefinitionData";
import { getProcessCounts } from "../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { FragmentNodeType } from "../../../../types/node";
import { ErrorBoundary } from "../../../common/error-boundary/ErrorBoundary";
import { SectionErrorFallbackComponent } from "../../../common/error-boundary/fallbackComponent/DialogErrorFallbackComponent";
import type { Scenario } from "../../../Process/types";
import { FragmentGraphPreview } from "../../fragmentGraph";
import NodeUtils from "../../NodeUtils";

export function FragmentContent({ nodeToDisplay }: { nodeToDisplay: FragmentNodeType }): React.JSX.Element {
    const processCounts = useAppSelector(getProcessCounts);
    const processDefinitionData = useAppSelector(getProcessDefinitionData);

    const [fragmentContent, setFragmentContent] = useState<Scenario>(null);
    const { openFragmentNodes } = useModalsIfNeeded();

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

    if (!fragmentContent) {
        return null;
    }

    return (
        <Box sx={(theme) => ({ background: theme.palette.background.default, display: "grid", overflow: "hidden", minHeight: "400px" })}>
            <ErrorBoundary FallbackComponent={SectionErrorFallbackComponent}>
                <FragmentGraphPreview
                    processCounts={fragmentCounts}
                    scenario={fragmentContent}
                    nodeIdPrefixForFragmentTests={getFragmentNodesPrefix(fragmentContent)}
                />
            </ErrorBoundary>
        </Box>
    );
}
