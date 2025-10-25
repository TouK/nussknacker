import type { ThunkAction } from "./reduxTypes";

export type AssistantActions =
    | { type: "ASSISTANT_OPEN" }
    | { type: "ASSISTANT_CLOSE" }
    | { type: "ASSISTANT_FOCUS" }
    | { type: "ASSISTANT_ASK"; question: string; realPrompt?: string };

const delay = (time = 250) => new Promise((resolve) => setTimeout(resolve, time));

export function assistantAsk(question: string, realPrompt?: string): ThunkAction {
    return async (dispatch) => {
        dispatch({ type: "ASSISTANT_OPEN" });
        dispatch({ type: "ASSISTANT_ASK", question, realPrompt });
        await delay();
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
