import { getUserSettings } from "../../reducers/selectors/userSettings";
import type { Setting, UserSettings } from "../../reducers/userSettings";
import { getDefaultUserSettings } from "../../reducers/userSettings";
import type { Action, ThunkAction } from "../reduxTypes";

export function userSettingSet(key: Setting, value: boolean | string): Action {
    return {
        type: "USERSETTING_SET",
        key,
        value,
    };
}

export function userSettingsRotate(key: Setting, values = ["default", false, true]): ThunkAction {
    return (dispatch, getState) => {
        const current = getUserSettings(getState(), false)[key];
        const index = Math.max(
            0,
            values.findIndex((v) => v === current),
        );
        dispatch(userSettingSet(key, values[(index + 1) % values.length]));
    };
}

export function userSettingsToggle(settings: Setting[]): ThunkAction {
    return (dispatch, getState) => {
        settings.forEach((setting) => {
            dispatch(userSettingsRotate(setting, [true, false]));
        });
    };
}

export function userSettingsSetInitial(flags: UserSettings): Action {
    return {
        type: "USERSETTINGS_DEFAULTS_LOADED",
        settings: getDefaultUserSettings(flags),
    };
}

export type UserSettingsActions =
    | {
          type: "USERSETTINGS_RESET";
      }
    | {
          type: "USERSETTING_SET";
          key: Setting;
          value: boolean | string;
      }
    | {
          type: "USERSETTINGS_DEFAULTS_LOADED";
          settings: UserSettings;
      };
