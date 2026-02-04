import { useCallback } from "react";

import type { SurveySettings } from "../../actions/nk/assignSettings";
import { userSettingSet } from "../../actions/nk/userSettings";
import { useUserSettings } from "../../common/useUserSettings";
import { getSurveySettings } from "../../reducers/selectors/settings";
import type { Setting } from "../../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";

export function useSurvey(): [SurveySettings | false, () => void] {
    const survey = useAppSelector(getSurveySettings);
    const dispatch = useAppDispatch();

    const settingsKey: Setting = `survey.${survey?.key}.closed`;
    const [closed] = useUserSettings(settingsKey);

    const hideSurvey = useCallback(() => dispatch(userSettingSet(settingsKey, true)), [dispatch, settingsKey]);

    return [!closed && survey, hideSurvey];
}
