import { useCallback, useMemo } from "react";

import { setSettings, toggleSettings } from "../actions/nk/userSettings";
import { getUserSettings } from "../reducers/selectors/userSettings";
import type { UserSettings } from "../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../store/configureStore";

export const useUserSettings: () => [UserSettings, (keys: Array<keyof UserSettings>) => void, (value: UserSettings) => void] = () => {
    const dispatch = useAppDispatch();
    const current = useAppSelector(getUserSettings);

    const toggle = useCallback(
        (keys: Array<keyof UserSettings>) => {
            dispatch(toggleSettings(keys));
        },
        [dispatch],
    );

    const reset = useCallback(
        (value: UserSettings) => {
            dispatch(setSettings(value));
        },
        [dispatch],
    );
    return useMemo(() => [current, toggle, reset], [current, reset, toggle]);
};
