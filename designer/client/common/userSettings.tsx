import {useDispatch, useSelector} from "react-redux"
import {setSettings, toggleSettings} from "../actions/nk/userSettings"
import {getUserSettings} from "../reducers/selectors/userSettings"
import {UserSettings} from "../reducers/userSettings"

export const useUserSettings: () => [UserSettings, (keys: Array<keyof UserSettings>) => void, (value: UserSettings) => void] = () => {
  const dispatch = useDispatch()
  const current = useSelector(getUserSettings)
  const toggle = (keys: Array<keyof UserSettings>) => { dispatch(toggleSettings(keys)) }
  const reset = (value: UserSettings) => { dispatch(setSettings(value)) }
  return [current, toggle, reset]
}
