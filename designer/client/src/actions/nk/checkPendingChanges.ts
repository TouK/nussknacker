import { getHasPendingChanges, getPendingChangesKeys } from "../../components/graph/node-modal/node/useEditState";
import { warn } from "../notificationActions";

const delay = (interval: number) => new Promise((r) => setTimeout(r, interval));

const waitForState = async (predicate: () => boolean, { interval = 50, timeout = 5000 } = {}) => {
    const start = Date.now();
    const repeat = true;
    while (repeat) {
        if (predicate()) return true;
        if (Date.now() - start > timeout) return false;
        await delay(interval);
    }
};

export const checkPendingChanges =
    (timeout = 5000) =>
    async (dispatch, getState) => {
        const predicate = () => !getHasPendingChanges(getState());
        if (predicate()) return;

        dispatch({ type: "NODE_DETAILS_NEEDS_APPLY", keys: getPendingChangesKeys(getState()) });
        if (await waitForState(predicate, { timeout })) return;

        const message = "still have pending changes!";
        dispatch(warn(message));

        throw message;
    };
