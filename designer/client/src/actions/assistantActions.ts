import { getContextPrompt, getContextRawData } from "../reducers/selectors/assistantSelectors";
import { getUserSettings } from "../reducers/selectors/userSettings";
import { delay } from "../utils";
import type { ThunkAction } from "./reduxTypes";

export type AssistantActions =
    | { type: "ASSISTANT_OPEN" }
    | { type: "ASSISTANT_CLOSE" }
    | { type: "ASSISTANT_FOCUS" }
    | { type: "ASSISTANT_ASK"; question: string; realPrompt?: string; contextRawData?: string };

export function assistantAsk(question: string, prompt?: string): ThunkAction {
    return async (dispatch, getState) => {
        dispatch({ type: "ASSISTANT_OPEN" });
        await delay();

        const state = getState();
        const settings = getUserSettings(state);
        const includeScenarioData = settings["assistant.includeScenarioData"];

        if (prompt && includeScenarioData) {
            const contextRawData = getContextRawData(state);
            const contextPrompt = getContextPrompt(state);
            dispatch({
                type: "ASSISTANT_ASK",
                question,
                realPrompt: `${prompt}. (${contextPrompt})`,
                contextRawData,
            });
        } else {
            dispatch({ type: "ASSISTANT_ASK", question, realPrompt: prompt });
        }

        dispatch({ type: "ASSISTANT_FOCUS" });
    };
}

export function assistantOpen(): ThunkAction {
    return async (dispatch) => {
        dispatch({ type: "ASSISTANT_OPEN" });
        await delay();
        dispatch({ type: "ASSISTANT_FOCUS" });
    };
}

export function assistantClose(): ThunkAction {
    return (dispatch) => {
        dispatch({ type: "ASSISTANT_CLOSE" });
    };
}
