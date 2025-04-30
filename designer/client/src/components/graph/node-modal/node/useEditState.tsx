import { useCallback, useEffect, useId } from "react";
import { useDispatch, useSelector } from "react-redux";
import { createSelector } from "reselect";

import { getUi } from "../../../../reducers/selectors/ui";
import type { EditState } from "./useNodeState";

const getPendingChanges = createSelector(getUi, ({ pendingChanges }) => pendingChanges);
export const getHasPendingChanges = createSelector(
    getPendingChanges,
    (pendingChanges) => Object.values(pendingChanges).filter((s) => s !== "idle").length > 0,
);

export function useEditState(): [EditState, (value?: EditState) => void] {
    const dispatch = useDispatch();
    const id = useId();

    const state = useSelector(getPendingChanges);
    const setState = useCallback(
        (value?: EditState) => {
            dispatch({
                type: "SET_PENDING_CHANGES",
                id,
                pendingChanges: value,
            });
        },
        [dispatch, id],
    );

    useEffect(() => {
        setState("idle");
        return () => {
            setState();
        };
    }, [setState]);

    return [state[id], setState];
}
