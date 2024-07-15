import moment, { Moment } from "moment";
import HttpService from "../../http/HttpService";
import { ThunkAction } from "../reduxTypes";
import { ProcessCounts } from "../../reducers/graph";
import { ScenarioGraph } from "../../types";
import { getProcessCountsRefresh } from "../../reducers/selectors/graph";

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

const checkPossibleCountsToCalculate = (processCounts: ProcessCounts, scenarioGraph: ScenarioGraph) => {
    const processCountsName = Object.keys(processCounts).sort((a, b) => a.localeCompare(b));
    const uncountableNodes = scenarioGraph.nodes
        .sort((a, b) => a.id.localeCompare(b.id))
        .filter((node, index) => node.id !== processCountsName[index]);
    const newProcessCounts = { ...processCounts };
    if (uncountableNodes.length !== 0 && processCountsName.length !== 0) {
        for (let i = 0; i < uncountableNodes.length; i++) {
            newProcessCounts[uncountableNodes[i].id] = {
                all: undefined,
                errors: 0,
                fragmentCounts: {},
            };
        }
    }
    return newProcessCounts;
};

const MIN_REFRESH_TIME = 10000;

let refreshTimeout: NodeJS.Timeout;

export function fetchAndDisplayProcessCounts(params: {
    processName: string;
    from: Moment;
    to: Moment;
    scenarioGraph: ScenarioGraph;
    refreshIn?: number | false;
}): ThunkAction<Promise<void>> {
    const { processName, from, to, scenarioGraph, refreshIn = false } = params;
    return async (dispatch, getState) => {
        clearTimeout(refreshTimeout);

        const counts = await HttpService.fetchProcessCounts(processName, from, to).then(({ data }) =>
            checkPossibleCountsToCalculate(data, scenarioGraph),
        );

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
