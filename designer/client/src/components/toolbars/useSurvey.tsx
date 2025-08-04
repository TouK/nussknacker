import { useCallback } from "react";

import type { SurveySettings } from "../../actions/nk";
import { useUserSettings } from "../../common/userSettings";
import { getSurveySettings } from "../../reducers/selectors/settings";
import { useAppSelector } from "../../store/storeHelpers";

export function useSurvey(): [SurveySettings | false, () => void] {
    const survey = useAppSelector(getSurveySettings);
    const [userSettings, , setSettings] = useUserSettings();
    const settingsKey = `survey.${survey?.key}.closed`;

    const showSurvey = !userSettings[settingsKey];
    const hideSurvey = useCallback(() => setSettings({ ...userSettings, [settingsKey]: true }), [setSettings, settingsKey, userSettings]);

    return [showSurvey && survey, hideSurvey];
}
