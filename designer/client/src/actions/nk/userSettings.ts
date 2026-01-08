import { userSettings } from "../../reducers/selectors/userSettings";
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

export function userSettingDelete(key: Setting): Action {
    return {
        type: "USERSETTING_DELETE",
        key,
    };
}

function nextIndex<T>(options: T[], selected: T): number {
    const index = options.indexOf(selected);
    if (index === -1) return 1;
    return (index + 1) % options.length;
}

export function userSettingsRotate(key: Setting, values = ["default", true, false]): ThunkAction {
    return (dispatch, getState) => {
        const current = userSettings(getState()).values[key];
        const nextValue = values[nextIndex(values, current)];
        dispatch(userSettingSet(key, nextValue));
    };
}

export function userSettingsToggle(settings: Setting[]): ThunkAction {
    return (dispatch) => {
        settings.forEach((setting) => {
            dispatch(userSettingsRotate(setting, [false, true]));
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
          type: "USERSETTING_DELETE";
          key: Setting;
      }
    | {
          type: "USERSETTINGS_DEFAULTS_LOADED";
          settings: UserSettings;
      };
