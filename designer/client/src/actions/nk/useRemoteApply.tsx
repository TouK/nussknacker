import { useEffect, useRef } from "react";

import { getUserSettings } from "../../reducers/selectors/userSettings";
import { addListenerTyped, useAppDispatch, useAppSelector } from "../../store/storeHelpers";

export function useRemoteApply(key: string, callback: () => void) {
    const dispatch = useAppDispatch();
    const savedCallback = useRef(callback);

    useEffect(() => {
        savedCallback.current = callback;
    }, [callback]);

    const settings = useAppSelector(getUserSettings);
    const autoApply = settings["node.autoApply"];
    useEffect(() => {
        if (!autoApply) return;
        return dispatch(
            addListenerTyped("NODE_DETAILS_NEEDS_APPLY", ({ keys }) => {
                if (keys.includes(key)) {
                    savedCallback.current?.();
                }
            }),
        );
    }, [autoApply, dispatch, key]);
}
