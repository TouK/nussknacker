import type { Direction } from "../../components/graph/node-modal/io/VariableContextTree";
import HttpService from "../../http/HttpService";
import type { ResultsWithCountsDto } from "../../http/resultsWithCountsDto";
import { getHasPauseReasons, getIsLiveDataWorking, getVisibleDataType, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getScenario } from "../../reducers/selectors/graph";
import type { ThunkAction } from "../reduxTypes";

export type Initiator = "tests" | "button" | "list" | `${Direction}_accordion` | null;
export type LiveDataActions =
    | { type: "LIVE_DATA_START"; initiator: Initiator }
    | { type: "LIVE_DATA_STARTED" }
    | { type: "FETCH_LIVE_DATA" }
    | {
          type: "DISPLAY_LIVE_DATA";
          results: ResultsWithCountsDto;
          nextIn: number;
      }
    | { type: "LIVE_DATA_STOP"; initiator: Initiator };

const REFRESH_TIME = 1000;

let intervalId: number;

function fetchAndDisplayLiveData(showErrors = false): ThunkAction {
    return (dispatch, getState) => {
        async function perform(showErrors = false) {
            dispatch({ type: "FETCH_LIVE_DATA" });
            const scenario = getScenario(getState());
            const { data: results } = await HttpService.fetchProcessLiveData(scenario.name, showErrors);

            const state = getState();
            if (!(isReadyForLiveData(state) && getIsLiveDataWorking(state))) {
                dispatch(stopLiveData());
                return;
            }

            if (intervalId && ["test", "counts"].includes(getVisibleDataType(state))) {
                dispatch(stopLiveData("tests"));
                return;
            }

            dispatch({
                type: "DISPLAY_LIVE_DATA",
                results,
                nextIn: REFRESH_TIME,
            });
        }

        perform(showErrors);

        if (!intervalId) {
            dispatch({ type: "LIVE_DATA_STARTED" });
            intervalId = window.setInterval(perform, REFRESH_TIME);
        }
    };
}

export function startLiveData(initiator: Initiator = null, showErrors = false): ThunkAction {
    return async (dispatch, getState) => {
        await dispatch({ type: "LIVE_DATA_START", initiator });

        if (!getHasPauseReasons(getState())) {
            dispatch(fetchAndDisplayLiveData(showErrors));
        }
    };
}

export function stopLiveData(initiator: Initiator = null): ThunkAction {
    return (dispatch) => {
        dispatch({ type: "LIVE_DATA_STOP", initiator });
        window.clearInterval(intervalId);
        intervalId = null;
    };
}
