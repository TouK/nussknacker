import { useCallback } from "react";
import { useSelector } from "react-redux";

import type { SurveySettings } from "../../actions/nk";
import { useUserSettings } from "../../common/userSettings";
import { getSurveySettings } from "../../reducers/selectors/settings";

export function useSurvey(): [SurveySettings | false, () => void] {
    const survey = useSelector(getSurveySettings);
    const [userSettings, , setSettings] = useUserSettings();
    const settingsKey = `survey.${survey?.key}.closed`;

    const showSurvey = !userSettings[settingsKey];
    const hideSurvey = useCallback(() => setSettings({ ...userSettings, [settingsKey]: true }), [setSettings, settingsKey, userSettings]);

    return [showSurvey && survey, hideSurvey];
}
