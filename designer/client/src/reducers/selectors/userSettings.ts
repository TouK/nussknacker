import { omitBy } from "lodash";
import { RootState } from "../index";
import { UserSettings } from "../userSettings";
import { createSelector } from "reselect";

const userSettings = (state: RootState): UserSettings => state.userSettings;

export const getUserSettings = createSelector(userSettings, (s) => omitBy(s, (v, k) => k.startsWith("_")));
