import type { MutableRefObject } from "react";
import { useCallback, useEffect, useId, useImperativeHandle, useRef } from "react";
import { createSelector } from "reselect";

import type { RootState } from "../../../../reducers";
import { getUi } from "../../../../reducers/selectors/ui";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { EditState } from "./useNodeState";

const getPendingChanges = createSelector(getUi, ({ pendingChanges }) => pendingChanges);
export const getHasPendingChanges = createSelector(
    getPendingChanges,
    (pendingChanges) => Object.values(pendingChanges).filter((s) => s !== "idle").length > 0,
);

export function useEditState(): [EditState, (value?: EditState) => void, MutableRefObject<EditState>] {
    const dispatch = useAppDispatch();
    const id = useId();

    const editState = useAppSelector((state: RootState) => getPendingChanges(state)[id]);
    const editStateRef = useRef<EditState>(null);

    const setState = useCallback(
        (value?: EditState) => {
            editStateRef.current = value;
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

    useImperativeHandle(editStateRef, () => editState, [editState]);

    return [editState, setState, editStateRef];
}
