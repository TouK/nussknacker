import {SurveySettings} from "../../actions/nk"
import {useSelector} from "react-redux"
import {getSurveySettings} from "../../reducers/selectors/settings"
import {useUserSettings} from "../../common/userSettings"
import {useCallback} from "react"

export function useSurvey(): [SurveySettings | false, () => void] {
  const survey = useSelector(getSurveySettings)
  const [userSettings, , setSettings] = useUserSettings()
  const settingsKey = `survey-panel(${(survey?.key)}).closed`

  const showSurvey = !userSettings[settingsKey]
  const hideSurvey = useCallback(
    () => setSettings({...userSettings, [settingsKey]: true}),
    [setSettings, userSettings]
  )

  return [showSurvey && survey, hideSurvey]
}
