import moment from "moment/moment";

import HttpService from "../../http/HttpService";
import type { ResultsWithCountsDto } from "../../http/resultsWithCountsDto";
import { isReadyForLiveData } from "../../reducers/selectors/getLiveData";
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

const MIN_REFRESH_TIME = 500;
let refreshTimeout: NodeJS.Timeout;

export const stopLiveData = (): ThunkAction => (dispatch, getState) => {
    dispatch({ type: "LIVE_DATA_STOP" });
    clearTimeout(refreshTimeout);
};

export function fetchAndDisplayLiveData(refreshIn: number | false = 1): ThunkAction {
    return async (dispatch, getState) => {
        clearTimeout(refreshTimeout);
        if (!isReadyForLiveData(getState())) {
            return dispatch(stopLiveData());
        }

        const scenario = getScenario(getState());
        const { data: results } = await HttpService.fetchProcessLiveData(scenario.name);

        const now = moment();
        if (refreshIn) {
            const last = now.valueOf();
            const nextIn = Math.max(MIN_REFRESH_TIME, refreshIn * 1000);

            refreshTimeout = setTimeout(() => {
                dispatch(fetchAndDisplayLiveData(refreshIn));
            }, nextIn);

            dispatch(displayLiveData(results, { last, nextIn }));
        } else {
            dispatch(displayLiveData(results));
        }
    };
}
