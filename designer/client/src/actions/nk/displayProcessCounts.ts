import type { Moment } from "moment";
import moment from "moment";

import { replaceSearchQuery } from "../../containers/hooks/useSearchQuery";
import HttpService from "../../http/HttpService/instance";
import type { ProcessCounts } from "../../http/resultsWithCountsDto";
import { getProcessCountsRefresh, getScenarioGraph } from "../../reducers/selectors/graph";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import type { ThunkAction } from "../reduxTypes";

export type RefreshData = {
    last: number;
    nextIn: number;
};

export interface DisplayProcessCountsAction {
    type: "DISPLAY_PROCESS_COUNTS";
    processCounts: ProcessCounts;
    refresh?: RefreshData;
}

export function displayProcessCounts(processCounts: ProcessCounts, refresh?: RefreshData): DisplayProcessCountsAction {
    return {
        type: "DISPLAY_PROCESS_COUNTS",
        processCounts,
        refresh,
    };
}

export const clearProcessCounts = () => displayProcessCounts({});

const extendWithEmpty = (processCounts: ProcessCounts, scenarioGraph: ScenarioGraph): ProcessCounts => ({
    ...Object.fromEntries(scenarioGraph.nodes.map(({ id }) => [id, { fragmentCounts: {} }])),
    ...processCounts,
});

const MIN_REFRESH_TIME = 10000;

let refreshTimeout: NodeJS.Timeout;

export function fetchAndDisplayProcessCounts(params: {
    processName: string;
    from: Moment;
    to: Moment;
    refreshIn?: number | false;
}): ThunkAction {
    const { processName, from, to, refreshIn = false } = params;
    return async (dispatch, getState) => {
        clearTimeout(refreshTimeout);
        replaceSearchQuery((current) => ({
            ...current,
            from: from.toISOString(),
            to: to.toISOString(),
            refresh: refreshIn ? `${refreshIn}s` : undefined,
        }));

        const counts = await HttpService.fetchProcessCounts(processName, from, to).then(({ data }) => {
            const scenarioGraph = getScenarioGraph(getState());
            return extendWithEmpty(data, scenarioGraph);
        });

        if (!counts) return;

        const now = moment();
        const shouldQueueRefresh = refreshIn && now.isBefore(to);

        if (shouldQueueRefresh) {
            const last = now.valueOf();
            const nextIn = Math.max(MIN_REFRESH_TIME, refreshIn * 1000);

            refreshTimeout = setTimeout(() => {
                const countsRefresh = getProcessCountsRefresh(getState());
                if (countsRefresh) {
                    dispatch(fetchAndDisplayProcessCounts(params));
                }
            }, nextIn);

            dispatch(displayProcessCounts(counts, { last, nextIn }));
        } else {
            dispatch(displayProcessCounts(counts));
        }
    };
}

export type CountsActions = DisplayProcessCountsAction;
