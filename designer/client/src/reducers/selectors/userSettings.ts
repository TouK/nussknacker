import { createSelector } from "reselect";
import type { Entry } from "type-fest";

import type { RootState } from "../index";
import type { Setting, UserSettings } from "../userSettings";

export const userSettings = (state: RootState) => {
    return state.userSettings;
};

export const getUserSettingsValues = createSelector(userSettings, ({ values }): UserSettings => {
    return Object.fromEntries(
        Object.entries(values)
            .map(([key, value]: Entry<typeof values>) => (value !== "default" ? [key, value] : null))
            .filter(Boolean),
    );
});

export const getUserSettings = createSelector([userSettings, getUserSettingsValues], ({ defaults }, values): UserSettings => {
    return { ...defaults, ...values };
});

export const getUserSettingsMerged = createSelector([userSettings], ({ values, defaults }) => {
    const result: Partial<Record<Setting, { value: boolean; isDefault: boolean }>> = {};
    Object.entries(defaults).forEach(([key, value]: Entry<typeof defaults>) => {
        result[key] = { value, isDefault: true };
    });
    Object.entries(values).forEach(([key, value]: Entry<typeof values>) => {
        result[key] ??= { value: null, isDefault: true };
        if (value !== true && value !== false) return;
        result[key] = { value, isDefault: false };
    });
    return result;
});
