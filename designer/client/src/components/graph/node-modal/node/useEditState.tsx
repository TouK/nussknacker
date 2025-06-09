import type { MutableRefObject } from "react";
import { useCallback, useEffect, useId, useImperativeHandle, useRef } from "react";
import { useDispatch, useSelector } from "react-redux";
import { createSelector } from "reselect";

import type { RootState } from "../../../../reducers";
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

    const editState = useSelector((state: RootState) => getPendingChanges(state)[id]);

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

    return [editState, setState];
}
