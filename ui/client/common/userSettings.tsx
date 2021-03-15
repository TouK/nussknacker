import {useDispatch, useSelector} from "react-redux"
import {toggleSettings} from "../actions/nk/userSettings"
import {RootState} from "../reducers"
import {UserSettings} from "../reducers/userSettings"

export const useUserSettings: () => [UserSettings, (keys: Array<keyof UserSettings>) => void] = () => {
  const dispatch = useDispatch()
  const current = useSelector<RootState, UserSettings>(state => state.userSettings)
  const toggleFeatureFlags = (keys: Array<keyof UserSettings>) => { dispatch(toggleSettings(keys)) }
  return [current, toggleFeatureFlags]
}
