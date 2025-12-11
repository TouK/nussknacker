import { useCallback } from "react";

import type { SurveySettings } from "../../actions/nk/assignSettings";
import { userSettingSet } from "../../actions/nk/userSettings";
import { getSurveySettings } from "../../reducers/selectors/settings";
import { getUserSettings } from "../../reducers/selectors/userSettings";
import type { Setting } from "../../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";

export function useSurvey(): [SurveySettings | false, () => void] {
    const survey = useAppSelector(getSurveySettings);
    const userSettings = useAppSelector(getUserSettings);
    const dispatch = useAppDispatch();

    const settingsKey: Setting = `survey.${survey?.key}.closed`;

    const showSurvey = !userSettings[settingsKey];
    const hideSurvey = useCallback(() => dispatch(userSettingSet(settingsKey, true)), [dispatch, settingsKey]);

    return [showSurvey && survey, hideSurvey];
}
