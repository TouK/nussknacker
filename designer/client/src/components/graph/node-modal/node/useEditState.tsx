import type { MutableRefObject } from "react";
import { useCallback, useEffect, useImperativeHandle, useRef } from "react";
import { createSelector } from "reselect";

import type { RootState } from "../../../../reducers";
import { getUi } from "../../../../reducers/selectors/ui";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { EditState } from "./useNodeState";

export const getPendingChanges = createSelector(getUi, ({ pendingChanges }) => pendingChanges);
export const getPendingChangesKeys = createSelector(getPendingChanges, (pendingChanges) => {
    return Object.entries(pendingChanges)
        .filter(([k, s]) => s !== "idle")
        .map(([k]) => k);
});
export const getHasPendingChanges = createSelector(getUserSettings, getPendingChangesKeys, (userSettings, pendingChanges) => {
    return userSettings["node.autoApply"] && pendingChanges.length > 0;
});

export function useEditState(id: string): [EditState, (value?: EditState) => void, MutableRefObject<EditState>] {
    const dispatch = useAppDispatch();

    const editState = useAppSelector((state: RootState) => getPendingChanges(state)[id] || "idle");
    const editStateRef = useRef<EditState>(editState);

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
