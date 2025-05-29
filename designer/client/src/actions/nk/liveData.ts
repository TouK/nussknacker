import moment from "moment/moment";

import HttpService from "../../http/HttpService";
import type { ResultsWithCountsDto } from "../../http/resultsWithCountsDto";
import { getLiveDataRefresh, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getScenario } from "../../reducers/selectors/graph";
import type { Action, ThunkAction } from "../reduxTypes";
import type { RefreshData } from "./displayProcessCounts";

export type LiveDataActions =
    | { type: "LIVE_DATA_STOP" }
    | {
          type: "DISPLAY_LIVE_DATA";
          results: ResultsWithCountsDto;
          refresh?: RefreshData;
      };

function displayLiveData(results: ResultsWithCountsDto, refresh?: RefreshData): Action {
    return {
        type: "DISPLAY_LIVE_DATA",
        results,
        refresh,
    };
}

export const stopLiveData = (): ThunkAction => (dispatch, getState) => {
    const refresh = getLiveDataRefresh(getState());
    setTimeout(() => {
        dispatch({ type: "LIVE_DATA_STOP" });
    }, (refresh?.nextIn || 0) * 0.5);
};

const MIN_REFRESH_TIME = 500;
let refreshTimeout: NodeJS.Timeout;

export function fetchAndDisplayLiveData(refreshIn: number | false = 1): ThunkAction {
    return async (dispatch, getState) => {
        clearTimeout(refreshTimeout);

        let results: ResultsWithCountsDto;
        if (isReadyForLiveData(getState())) {
            const scenario = getScenario(getState());
            results = await HttpService.fetchProcessLiveData(scenario.name).then(({ data }) => data);
        }
        if (!results) return;

        const now = moment();
        if (refreshIn) {
            const last = now.valueOf();
            const nextIn = Math.max(MIN_REFRESH_TIME, refreshIn * 1000);

            refreshTimeout = setTimeout(() => {
                const liveDataRefresh = getLiveDataRefresh(getState());
                if (liveDataRefresh) {
                    dispatch(fetchAndDisplayLiveData(refreshIn));
                }
            }, nextIn);

            dispatch(
                displayLiveData(results, {
                    last,
                    nextIn,
                }),
            );
        } else {
            dispatch(displayLiveData(results));
        }
    };
}
