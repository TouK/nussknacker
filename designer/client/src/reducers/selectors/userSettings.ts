import { createSelector } from "reselect";

import type { RootState } from "../index";
import type { Setting, UserSettings } from "../userSettings";

export const userSettings = (state: RootState) => {
    return state.userSettings;
};

export const getUserSettingsValues = createSelector(
    [userSettings, (_: RootState, skipDefaults = true) => skipDefaults],
    ({ values }, skipDefaults): UserSettings => {
        return skipDefaults
            ? Object.fromEntries(
                  Object.entries(values)
                      .map(([key, value]) => (value !== "default" ? [key, value] : null))
                      .filter(Boolean),
              )
            : values;
    },
);

export const getUserSettings = createSelector([userSettings, getUserSettingsValues], ({ defaults }, values): UserSettings => {
    return { ...defaults, ...values };
});

export const getUserSettingsMerged = createSelector(
    [userSettings],
    ({ values, defaults }): Record<Setting, { value: boolean; isDefault: boolean }> => {
        const result: Record<Setting, { value: boolean; isDefault: boolean }> = {};
        Object.entries(defaults).forEach(([key, value]) => {
            result[key] = { value, isDefault: true };
        });
        Object.entries(values).forEach(([key, value]) => {
            result[key] ??= { value: null, isDefault: true };
            if (value !== true && value !== false) return;
            result[key] = { value, isDefault: false };
        });
        return result;
    },
);
