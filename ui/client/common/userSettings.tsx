import {useDispatch, useSelector} from "react-redux"
import {toggleSettings} from "../actions/nk/userSettings"
import {getUserSettings} from "../reducers/selectors/userSettings"
import {UserSettings} from "../reducers/userSettings"

export const useUserSettings: () => [UserSettings, (keys: Array<keyof UserSettings>) => void] = () => {
  const dispatch = useDispatch()
  const current = useSelector(getUserSettings)
  const toggle = (keys: Array<keyof UserSettings>) => { dispatch(toggleSettings(keys)) }
  return [current, toggle]
}
