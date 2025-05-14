import { omitBy } from "lodash";
import { createSelector } from "reselect";

import type { RootState } from "../index";
import type { UserSettings } from "../userSettings";

const userSettings = (state: RootState): UserSettings => state.userSettings;

export const getUserSettings = createSelector(userSettings, (s): UserSettings => omitBy(s, (v, k) => k.startsWith("_")));
