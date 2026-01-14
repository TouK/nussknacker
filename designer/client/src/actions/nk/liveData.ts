import axios from "axios";

import HttpService from "../../http/HttpService/instance";
import type { ResultsWithCountsDto } from "../../http/resultsWithCountsDto";
import { VisibleDataType } from "../../reducers/graph/types";
import { getHasPauseReasons, getIsLiveDataWorking, getVisibleDataType, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getScenario } from "../../reducers/selectors/graph";
import type { ThunkAction } from "../reduxTypes";
import { AbortControllersStack } from "./abortControllersStack";
import { hideTestRunDetails } from "./process";

export enum Initiator {
    init = "initial",
    tests = "tests",
    button = "button",
    list = "list",
    inputAccordion = "input_accordion",
    outputAccordion = "output_accordion",
    errorHandler = "error",
}

export type LiveDataActions =
    | { type: "LIVE_DATA_START"; initiator: Initiator | null }
    | { type: "LIVE_DATA_STARTED" }
    | { type: "FETCH_LIVE_DATA" }
    | {
          type: "DISPLAY_LIVE_DATA";
          results: ResultsWithCountsDto;
          nextIn: number;
      }
    | { type: "LIVE_DATA_STOP"; initiator: Initiator | null };

const REFRESH_TIME = 1000;

let intervalId: number = null;

function fetchAndDisplayLiveData(showErrors = false, refresh = REFRESH_TIME): ThunkAction {
    return (dispatch, getState) => {
        async function perform(showErrors = false) {
            const abortController = AbortControllersStack.next();

            dispatch({ type: "FETCH_LIVE_DATA" });
            const scenario = getScenario(getState());
            try {
                const { data: results } = await HttpService.fetchProcessLiveData(scenario.name, showErrors, abortController);

                const state = getState();
                if (!(isReadyForLiveData(state) && getIsLiveDataWorking(state))) {
                    dispatch(stopLiveData());
                    return;
                }

                if (intervalId && [VisibleDataType.test, VisibleDataType.counts].includes(getVisibleDataType(state))) {
                    dispatch(stopLiveData(Initiator.tests));
                    return;
                }

                dispatch({
                    type: "DISPLAY_LIVE_DATA",
                    results,
                    nextIn: REFRESH_TIME,
                });
            } catch (error) {
                if (axios.isCancel(error)) return;
                dispatch(stopLiveData(Initiator.errorHandler));
            } finally {
                abortController.abort();
            }
        }

        perform(showErrors);

        if (!intervalId) {
            dispatch({ type: "LIVE_DATA_STARTED" });
            intervalId = window.setInterval(perform, refresh);
        }
    };
}

export function startLiveData(initiator: Initiator | null = null, showErrors = false): ThunkAction {
    return async (dispatch, getState) => {
        dispatch({ type: "LIVE_DATA_START", initiator });
        AbortControllersStack.abortAll();
        if (!getHasPauseReasons(getState())) {
            if (!getIsLiveDataWorking(getState())) {
                dispatch(hideTestRunDetails());
                dispatch({ type: "CLEAR_TEST_ASSERTIONS_RESULTS" });
            }
            dispatch(fetchAndDisplayLiveData(showErrors));
        }
    };
}

export function stopLiveData(initiator: Initiator = null): ThunkAction {
    return (dispatch) => {
        dispatch({ type: "LIVE_DATA_STOP", initiator });
        window.clearInterval(intervalId);
        AbortControllersStack.abortAll();
        intervalId = null;
    };
}
