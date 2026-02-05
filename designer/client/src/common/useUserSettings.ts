import { useMemo } from "react";

import { getUserSettings } from "../reducers/selectors/userSettings";
import type { Setting } from "../reducers/userSettings";
import { useAppSelector } from "../store/storeHelpers";

export function useUserSettings(...flags: Setting[]): boolean[] {
    const userSettings = useAppSelector(getUserSettings);
    return useMemo(() => flags.map((flag: Setting) => userSettings?.[flag]), [flags, userSettings]);
}
