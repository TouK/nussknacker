import {useDispatch, useSelector} from "react-redux"
import {setFlags, toggleFlags} from "../actions/nk/featureFlags"
import {store} from "../bootstrap"
import {RootState} from "../reducers"
import {FeatureFlags} from "../reducers/featureFlags"

export const useFeatureFlags: () => [FeatureFlags, (keys: Array<keyof FeatureFlags>) => void] = () => {
  const dispatch = useDispatch()
  const current = useSelector<RootState, FeatureFlags>(state => state.featureFlags)
  const toggleFeatureFlags = (keys: Array<keyof FeatureFlags>) => { dispatch(toggleFlags(keys)) }
  return [current, toggleFeatureFlags]
}

// expose to console
window["__FF"] = (flags: FeatureFlags) => {
  store.dispatch(setFlags(flags))
}
